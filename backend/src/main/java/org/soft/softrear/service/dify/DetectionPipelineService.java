package org.soft.softrear.service.dify;

import org.soft.softrear.pojo.dto.dify.DifyDecisionResult;
import org.soft.softrear.pojo.dto.dify.DronePipelineResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

@Service
public class DetectionPipelineService {
    private static final int MAX_DIFY_MEDIA_URL_LENGTH = 512;
    private static final int MAX_DIFY_TEXT_LENGTH = 500;
    private static final String LOCAL_MEDIA_PLACEHOLDER = "local-media";
    private static final Map<String, String> KITTI_LABELS = Map.of(
            "0", "Pedestrian",
            "1", "Cyclist",
            "2", "Car"
    );

    private final DifyWorkflowService difyWorkflowService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${detection.yolo-base-url:http://127.0.0.1:9000}")
    private String yoloBaseUrl;

    @Value("${detection.mmdet3d-base-url:http://127.0.0.1:8000}")
    private String mmdet3dBaseUrl;

    @Value("${detection.kitti-dataset-root:}")
    private String kittiDatasetRoot;

    public DetectionPipelineService(DifyWorkflowService difyWorkflowService) {
        this.difyWorkflowService = difyWorkflowService;
    }

    public DronePipelineResult run(String mediaType,
                                   MultipartFile file,
                                   MultipartFile imageFile,
                                   MultipartFile calibFile,
                                   String imagePathHint,
                                   Double conf,
                                   Double iou,
                                   Double scoreThr,
                                   String missionContext,
                                   String droneId) throws IOException {
        String normalizedType = normalizeMediaType(mediaType);
        Map<String, Object> detection = "pointcloud".equals(normalizedType)
                ? callMmdet3d(file, imageFile, calibFile, imagePathHint, scoreThr)
                : callYolo(file, conf, iou);

        Map<String, Object> difyStatus = difyWorkflowService.probeStatus();
        DifyDecisionResult dify = Boolean.TRUE.equals(difyStatus.get("ready"))
                ? difyWorkflowService.runWorkflow(
                        buildDifyInputs(normalizedType, file, detection, missionContext, droneId),
                        StringUtils.hasText(droneId) ? droneId : "demo-drone-001"
                )
                : DifyDecisionResult.skipped(String.valueOf(difyStatus.getOrDefault("probeMessage", "Dify is unavailable")));

        return new DronePipelineResult("pointcloud".equals(normalizedType) ? "mmdet3d" : "yolo", detection, dify);
    }

    private Map<String, Object> callYolo(MultipartFile file, Double conf, Double iou) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", multipartResource(file));
        body.add("conf", String.valueOf(conf == null ? 0.25 : conf));
        body.add("iou", String.valueOf(iou == null ? 0.45 : iou));
        return postMultipart(normalizeBaseUrl(yoloBaseUrl) + "/predict", body);
    }

    private Map<String, Object> callMmdet3d(MultipartFile file,
                                            MultipartFile imageFile,
                                            MultipartFile calibFile,
                                            String imagePathHint,
                                            Double scoreThr) throws IOException {
        MultipartFile leftImageFile = resolveLeftImageFile(file, imageFile);
        if (leftImageFile == null || leftImageFile.isEmpty()) {
            throw new IllegalArgumentException("Left image file is required");
        }

        MultipartFile pointCloudUpload = isKittiBin(file) ? file : null;
        MultipartFile calibUpload = calibFile != null && !calibFile.isEmpty() ? calibFile : null;
        String stem = fileStem(leftImageFile.getOriginalFilename());
        KittiSampleFiles sampleFiles = (pointCloudUpload != null && calibUpload != null)
                ? null
                : resolveKittiSampleFiles(stem, imagePathHint);

        Path calibTemp = null;
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("point_cloud_file", pointCloudUpload != null
                    ? multipartResource(pointCloudUpload)
                    : pathResource(sampleFiles.pointCloud(), sampleFiles.pointCloud().getFileName().toString()));
            body.add("image_file", multipartResource(leftImageFile));
            if (calibUpload != null) {
                calibTemp = writeMultipartToTempFile(calibUpload, "soft-rear-calib-", ".txt");
                body.add("calib_file", pathResource(calibTemp, calibTemp.getFileName().toString()));
            } else {
                body.add("calib_file", pathResource(sampleFiles.calib(), sampleFiles.calib().getFileName().toString()));
            }
            body.add("score_thr", String.valueOf(scoreThr == null ? 0.3 : scoreThr));

            Map<String, Object> detection = postMultipart(normalizeBaseUrl(mmdet3dBaseUrl) + "/predict", body);
            Path projectionCalib = calibTemp != null ? calibTemp : sampleFiles.calib();
            String calibrated = renderCalibratedProjection(leftImageFile, projectionCalib, detection);
            if (StringUtils.hasText(calibrated)) {
                detection.put("image_visualization", calibrated);
                detection.put("projection_source", "calib");
                detection.put("sample_id", stem);
            }
            return detection;
        } finally {
            if (calibTemp != null) {
                Files.deleteIfExists(calibTemp);
            }
        }
    }

    private Map<String, Object> postMultipart(String url, MultiValueMap<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        Map<String, Object> result = new HashMap<>();
        if (response.getBody() != null) {
            response.getBody().forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }

    private ByteArrayResource multipartResource(MultipartFile file) throws IOException {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }

    private ByteArrayResource pathResource(Path path, String filename) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private Map<String, Object> buildDifyInputs(String mediaType,
                                                MultipartFile file,
                                                Map<String, Object> detection,
                                                String missionContext,
                                                String droneId) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("media_url", resolveMediaUrl(file, detection));
        inputs.put("media_type", limitText(mediaType, 64, "image"));
        inputs.put("mission_context", buildMissionContext(missionContext, detection));
        inputs.put("drone_id", limitText(droneId, 64, "demo-drone-001"));
        return inputs;
    }

    private String resolveMediaUrl(MultipartFile file, Map<String, Object> detection) {
        String candidate = null;
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            candidate = fileStem(file.getOriginalFilename());
        }
        if (!StringUtils.hasText(candidate)) {
            Object sampleId = detection.get("sample_id");
            if (sampleId != null && StringUtils.hasText(String.valueOf(sampleId))) {
                candidate = String.valueOf(sampleId);
            }
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = LOCAL_MEDIA_PLACEHOLDER;
        }
        return limitText(candidate.replaceAll("[^a-zA-Z0-9._:-]", "_"), 120, LOCAL_MEDIA_PLACEHOLDER);
    }

    private boolean isUsableForDify(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.startsWith("data:")) {
            return false;
        }
        return normalized.length() < MAX_DIFY_MEDIA_URL_LENGTH;
    }

    private String buildMissionContext(String missionContext, Map<String, Object> detection) {
        Object detections = detection.get("detections");
        int count = detections instanceof List<?> list ? list.size() : 0;
        String base = limitText(missionContext, 160, "请根据检测结果给出无人机下一步安全动作。");
        String summary = "检测目标数量: " + count + "\n检测结果摘要: " + summarizeDetections(detections);
        return limitText(base + "\n" + summary, MAX_DIFY_TEXT_LENGTH, base);
    }

    private String summarizeDetections(Object detections) {
        if (!(detections instanceof List<?> list) || list.isEmpty()) {
            return "未检测到目标";
        }
        return list.stream().limit(5).map(this::summarizeDetectionItem).toList().toString();
    }

    private String summarizeDetectionItem(Object detection) {
        if (detection instanceof Map<?, ?> map) {
            Object label = normalizeKittiLabel(firstNonEmpty(map.get("class_name"), map.get("class"), map.get("label")));
            Object score = firstNonEmpty(map.get("score"), map.get("confidence"), map.get("conf"));
            return String.valueOf(label == null ? "object" : label) + "@" + String.valueOf(score == null ? "-" : score);
        }
        return String.valueOf(detection);
    }

    private String normalizeMediaType(String mediaType) {
        if ("pointcloud".equalsIgnoreCase(mediaType) || "mmdet3d".equalsIgnoreCase(mediaType)) {
            return "pointcloud";
        }
        return "image";
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String fileStem(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("Left image filename is required for KITTI auto matching");
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private KittiSampleFiles resolveKittiSampleFiles(String stem, String imagePathHint) {
        Path root = resolveKittiDatasetRoot();
        LinkedHashSet<Path> imageCandidates = new LinkedHashSet<>();

        Path hintedPath = resolveHintedPath(root, imagePathHint);
        if (hintedPath != null) {
            if (Files.isRegularFile(hintedPath)) {
                imageCandidates.add(hintedPath);
            } else if (Files.isDirectory(hintedPath)) {
                imageCandidates.addAll(findImageCandidates(hintedPath, stem));
            }
        }

        for (Path searchRoot : preferredKittiRoots(root)) {
            imageCandidates.addAll(findImageCandidates(searchRoot, stem));
        }

        for (Path imagePath : imageCandidates) {
            KittiSampleFiles sampleFiles = resolveSampleFilesFromImage(root, imagePath, stem);
            if (sampleFiles != null) {
                return sampleFiles;
            }
        }

        throw new IllegalArgumentException("Missing KITTI sample for stem " + stem + " under " + root);
    }

    private Path resolveKittiDatasetRoot() {
        List<Path> candidates = new ArrayList<>();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path repoRoot = cwd.getParent() != null && cwd.getFileName() != null && "SOFT-rear".equalsIgnoreCase(cwd.getFileName().toString())
                ? cwd.getParent()
                : cwd;

        if (StringUtils.hasText(kittiDatasetRoot)) {
            candidates.addAll(resolvePortablePathCandidates(kittiDatasetRoot, cwd, repoRoot));
        }

        for (String relative : List.of("kitti", "data/kitti", "../kitti", "../data/kitti", "../kitty", "../data/kitty")) {
            candidates.addAll(resolvePortablePathCandidates(relative, cwd, repoRoot));
        }

        for (Path candidate : candidates) {
            if (isKittiRoot(candidate)) {
                return candidate;
            }
        }
        String checked = candidates.stream().map(Path::toString).distinct().toList().toString();
        throw new IllegalArgumentException("KITTI dataset root not found. Set KITTI_DATASET_ROOT or detection.kitti-dataset-root. Checked: " + checked);
    }

    private List<Path> resolvePortablePathCandidates(String value, Path cwd, Path repoRoot) {
        String normalizedValue = value.trim().replace('\\', '/');
        if (!StringUtils.hasText(normalizedValue)) {
            return List.of();
        }
        try {
            Path raw = Paths.get(normalizedValue);
            if (raw.isAbsolute()) {
                return List.of(raw.normalize());
            }
            return List.of(
                    cwd.resolve(raw).normalize(),
                    repoRoot.resolve(raw).normalize()
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isKittiRoot(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        if (Files.isDirectory(path.resolve("image_2"))
                && Files.isDirectory(path.resolve("velodyne"))
                && Files.isDirectory(path.resolve("calib"))) {
            return true;
        }
        return (Files.isDirectory(path.resolve("training").resolve("image_2"))
                || Files.isDirectory(path.resolve("testing").resolve("image_2")));
    }

    private List<Path> preferredKittiRoots(Path root) {
        List<Path> roots = new ArrayList<>();
        Path training = root.resolve("training").normalize();
        Path testing = root.resolve("testing").normalize();
        if (Files.isDirectory(training)) {
            roots.add(training);
        }
        if (Files.isDirectory(testing)) {
            roots.add(testing);
        }
        if (roots.isEmpty() || roots.stream().noneMatch(root::equals)) {
            roots.add(root);
        }
        return roots.stream().distinct().toList();
    }

    private Path resolveHintedPath(Path root, String imagePathHint) {
        if (!StringUtils.hasText(imagePathHint)) {
            return null;
        }
        try {
            Path raw = Paths.get(imagePathHint.trim().replace('\\', '/'));
            Path resolved = raw.isAbsolute() ? raw.normalize() : root.resolve(raw).normalize();
            if (!resolved.startsWith(root)) {
                return null;
            }
            return resolved;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Path> findImageCandidates(Path root, String stem) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isKittiImageFile)
                    .filter(path -> stem.equalsIgnoreCase(fileStem(path.getFileName().toString())))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to scan KITTI images under " + root + ": " + e.getMessage(), e);
        }
    }

    private KittiSampleFiles resolveSampleFilesFromImage(Path root, Path imagePath, String stem) {
        Path sampleRoot = resolveSampleRoot(imagePath);
        List<Path> pointCloudCandidates = new ArrayList<>();
        List<Path> calibCandidates = new ArrayList<>();

        if (sampleRoot != null) {
            pointCloudCandidates.add(sampleRoot.resolve("velodyne").resolve(stem + ".bin"));
            calibCandidates.add(sampleRoot.resolve("calib").resolve(stem + ".txt"));
        }

        pointCloudCandidates.addAll(findMatchingFiles(root, "velodyne", stem + ".bin"));
        calibCandidates.addAll(findMatchingFiles(root, "calib", stem + ".txt"));

        Path pointCloud = firstExisting(pointCloudCandidates);
        Path calib = firstExisting(calibCandidates);
        if (pointCloud != null && calib != null) {
            return new KittiSampleFiles(pointCloud, calib);
        }
        return null;
    }

    private Path resolveSampleRoot(Path imagePath) {
        Path parent = imagePath.getParent();
        if (parent == null) {
            return null;
        }
        Path parentName = parent.getFileName();
        if (parentName != null && parentName.toString().toLowerCase(Locale.ROOT).startsWith("image")) {
            Path sampleRoot = parent.getParent();
            if (sampleRoot != null) {
                return sampleRoot;
            }
        }
        return parent;
    }

    private List<Path> findMatchingFiles(Path root, String folderName, String filename) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(filename))
                    .filter(path -> {
                        Path parent = path.getParent();
                        return parent != null
                                && parent.getFileName() != null
                                && parent.getFileName().toString().equalsIgnoreCase(folderName);
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to scan KITTI " + folderName + " files under " + root + ": " + e.getMessage(), e);
        }
    }

    private Path firstExisting(List<Path> candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Path writeMultipartToTempFile(MultipartFile file, String prefix, String fallbackSuffix) throws IOException {
        String suffix = fallbackSuffix;
        String originalName = file.getOriginalFilename();
        if (StringUtils.hasText(originalName)) {
            String extension = StringUtils.getFilenameExtension(originalName);
            if (StringUtils.hasText(extension)) {
                suffix = "." + extension;
            }
        }
        Path temp = Files.createTempFile(prefix, suffix);
        Files.write(temp, file.getBytes());
        return temp;
    }

    private MultipartFile resolveLeftImageFile(MultipartFile file, MultipartFile imageFile) {
        if (isImageFile(file)) {
            return file;
        }
        if (isImageFile(imageFile)) {
            return imageFile;
        }
        throw new IllegalArgumentException("Left image file is required");
    }

    private boolean isImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String name = file.getOriginalFilename();
        if (StringUtils.hasText(name)) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp") || lower.endsWith(".webp")) {
                return true;
            }
        }
        String contentType = file.getContentType();
        return StringUtils.hasText(contentType) && contentType.startsWith("image/");
    }

    private boolean isKittiBin(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String name = file.getOriginalFilename();
        return StringUtils.hasText(name) && name.toLowerCase(Locale.ROOT).endsWith(".bin");
    }

    private boolean isKittiImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp") || name.endsWith(".webp");
    }

    private String limitText(String text, int maxLength, String fallback) {
        String value = StringUtils.hasText(text) ? text.trim() : fallback;
        if (!StringUtils.hasText(value)) {
            value = fallback;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Object firstNonEmpty(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object normalizeKittiLabel(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (KITTI_LABELS.containsKey(text)) {
            return KITTI_LABELS.get(text);
        }
        String lower = text.toLowerCase();
        if ("car".equals(lower) || "pedestrian".equals(lower) || "cyclist".equals(lower)) {
            return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
        }
        return value;
    }

    private String renderCalibratedProjection(MultipartFile imageFile, Path calibFile, Map<String, Object> detection) {
        try (InputStream imageInput = imageFile.getInputStream();
             InputStream calibInput = Files.newInputStream(calibFile)) {
            BufferedImage image = ImageIO.read(imageInput);
            if (image == null) {
                return null;
            }
            Calibration calibration = parseCalibration(calibInput);
            if (calibration == null) {
                return null;
            }
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(2.0f));
                List<?> detections = detection.get("detections") instanceof List<?> list ? list : List.of();
                int colorIndex = 0;
                for (Object item : detections) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    BoxCorners corners = resolveBoxCorners(map);
                    if (corners == null || corners.points().length != 8) {
                        continue;
                    }
                    int[] xPoints = new int[8];
                    int[] yPoints = new int[8];
                    boolean valid = true;
                    for (int i = 0; i < corners.points().length; i++) {
                        double[] projected = projectPoint(corners.points()[i], corners.coordinate(), calibration);
                        if (projected == null) {
                            valid = false;
                            break;
                        }
                        xPoints[i] = (int) Math.round(projected[0]);
                        yPoints[i] = (int) Math.round(projected[1]);
                    }
                    if (!valid) {
                        continue;
                    }
                    Color color = pickColor(colorIndex++);
                    g.setColor(color);
                    drawBoxEdges(g, xPoints, yPoints);
                    drawBoxLabel(g, xPoints, yPoints, map, color);
                }
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private void drawBoxEdges(Graphics2D g, int[] x, int[] y) {
        int[][] edges = new int[][]{
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            g.drawLine(x[edge[0]], y[edge[0]], x[edge[1]], y[edge[1]]);
        }
    }

    private void drawBoxLabel(Graphics2D g, int[] x, int[] y, Map<?, ?> detection, Color color) {
        String className = String.valueOf(normalizeKittiLabel(
                firstNonEmpty(detection.get("class_name"), detection.get("class"), detection.get("label"))
        ));
        Object scoreValue = firstNonEmpty(detection.get("score"), detection.get("confidence"), detection.get("conf"));
        String scoreText = scoreValue == null ? "" : String.format(Locale.ROOT, " %.2f", toDouble(scoreValue));
        String labelText = (className == null || "null".equalsIgnoreCase(className)) ? "" : className + scoreText;
        if (!StringUtils.hasText(labelText)) {
            return;
        }

        int anchorX = Integer.MAX_VALUE;
        int anchorY = Integer.MAX_VALUE;
        for (int i = 0; i < x.length; i++) {
            anchorX = Math.min(anchorX, x[i]);
            anchorY = Math.min(anchorY, y[i]);
        }

        Font originalFont = g.getFont();
        g.setFont(originalFont.deriveFont(Font.BOLD, 14f));
        var metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(labelText);
        int textHeight = metrics.getHeight();
        int boxX = Math.max(4, anchorX);
        int boxY = Math.max(textHeight + 6, anchorY);

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(boxX - 4, boxY - textHeight, textWidth + 8, textHeight + 4, 8, 8);
        g.setColor(color);
        g.drawString(labelText, boxX, boxY - 4);
        g.setFont(originalFont);
    }

    private Color pickColor(int index) {
        Color[] palette = new Color[]{
                new Color(0x00, 0xD0, 0x7A),
                new Color(0xFF, 0x6B, 0x3D),
                new Color(0x2F, 0x8C, 0xFF),
                new Color(0xF0, 0xA5, 0x00),
                new Color(0xD4, 0x4B, 0xFF)
        };
        return palette[index % palette.length];
    }

    private double[] extractBox3d(Map<?, ?> map) {
        Object raw = firstNonEmpty(map.get("bbox_3d"), map.get("bbox3d"), map.get("box_3d"));
        if (raw instanceof List<?> list && list.size() >= 7) {
            double[] box = new double[7];
            for (int i = 0; i < 7; i++) {
                box[i] = toDouble(list.get(i));
            }
            return box;
        }
        if (raw instanceof String text) {
            String cleaned = text.replace('[', ' ').replace(']', ' ').trim();
            String[] tokens = cleaned.split("[,\\s]+");
            if (tokens.length >= 7) {
                double[] box = new double[7];
                for (int i = 0; i < 7; i++) {
                    box[i] = Double.parseDouble(tokens[i]);
                }
                return box;
            }
        }
        return null;
    }

    private BoxCorners resolveBoxCorners(Map<?, ?> map) {
        double[][] corners = extractCorners3d(map);
        if (corners != null) {
            return new BoxCorners(corners, detectBoxCoordinate(map));
        }
        double[] cameraBox = extractCameraBox3d(map);
        if (cameraBox != null) {
            return new BoxCorners(cameraBoxCorners(cameraBox), BoxCoordinate.CAMERA);
        }
        double[] box = extractBox3d(map);
        if (box != null) {
            return new BoxCorners(lidarBoxCorners(box), BoxCoordinate.LIDAR);
        }
        return null;
    }

    private BoxCoordinate detectBoxCoordinate(Map<?, ?> map) {
        Object raw = firstNonEmpty(
                map.get("box_type_3d"),
                map.get("bbox_3d_type"),
                map.get("coord_type"),
                map.get("coordinate")
        );
        String text = raw == null ? "" : String.valueOf(raw).toLowerCase(Locale.ROOT);
        if (text.contains("cam")) {
            return BoxCoordinate.CAMERA;
        }
        return BoxCoordinate.LIDAR;
    }

    private double[][] extractCorners3d(Map<?, ?> map) {
        Object raw = firstNonEmpty(
                map.get("corners_3d"),
                map.get("box_corners_3d"),
                map.get("bbox_3d_corners"),
                map.get("corners")
        );
        double[][] corners = toPointMatrix(raw, 8, 3);
        return corners != null ? corners : toFlatPointMatrix(raw, 8, 3);
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private double[] extractCameraBox3d(Map<?, ?> map) {
        Object raw = firstNonEmpty(
                map.get("bbox_3d_camera"),
                map.get("camera_bbox_3d"),
                map.get("cam_bbox_3d"),
                map.get("box_3d_camera")
        );
        if (raw instanceof List<?> list && list.size() >= 7) {
            double[] box = new double[7];
            for (int i = 0; i < 7; i++) {
                box[i] = toDouble(list.get(i));
            }
            return box;
        }
        if (raw instanceof String text) {
            String[] tokens = numericTokens(text);
            if (tokens.length >= 7) {
                double[] box = new double[7];
                for (int i = 0; i < 7; i++) {
                    box[i] = Double.parseDouble(tokens[i]);
                }
                return box;
            }
        }
        return null;
    }

    private double[][] lidarBoxCorners(double[] box) {
        double x = box[0];
        double y = box[1];
        double z = box[2];
        double dx = box[3];
        double dy = box[4];
        double dz = box[5];
        double yaw = box[6];
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double[][] local = new double[][]{
                {-dx / 2, -dy / 2, 0},
                {-dx / 2, -dy / 2, dz},
                {-dx / 2,  dy / 2, dz},
                {-dx / 2,  dy / 2, 0},
                { dx / 2, -dy / 2, 0},
                { dx / 2, -dy / 2, dz},
                { dx / 2,  dy / 2, dz},
                { dx / 2,  dy / 2, 0}
        };

        double[][] corners = new double[8][3];
        for (int i = 0; i < local.length; i++) {
            double lx = local[i][0];
            double ly = local[i][1];
            double lz = local[i][2];
            corners[i][0] = x + lx * cos - ly * sin;
            corners[i][1] = y + lx * sin + ly * cos;
            corners[i][2] = z + lz;
        }
        return corners;
    }

    private double[][] cameraBoxCorners(double[] box) {
        double x = box[0];
        double y = box[1];
        double z = box[2];
        double dx = box[3];
        double dy = box[4];
        double dz = box[5];
        double yaw = box[6];
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double[][] local = new double[][]{
                {-dx / 2, -dy, -dz / 2},
                {-dx / 2, -dy,  dz / 2},
                {-dx / 2,   0,  dz / 2},
                {-dx / 2,   0, -dz / 2},
                { dx / 2, -dy, -dz / 2},
                { dx / 2, -dy,  dz / 2},
                { dx / 2,   0,  dz / 2},
                { dx / 2,   0, -dz / 2}
        };

        double[][] corners = new double[8][3];
        for (int i = 0; i < local.length; i++) {
            double lx = local[i][0];
            double ly = local[i][1];
            double lz = local[i][2];
            corners[i][0] = x + lx * cos + lz * sin;
            corners[i][1] = y + ly;
            corners[i][2] = z - lx * sin + lz * cos;
        }
        return corners;
    }

    private double[] projectPoint(double[] point, BoxCoordinate coordinate, Calibration calibration) {
        double[] cam = coordinate == BoxCoordinate.CAMERA
                ? point
                : calibration.lidarToCamera(point[0], point[1], point[2]);
        if (cam[2] <= 0.1) {
            return null;
        }
        double[] projected = calibration.projectToImage(cam[0], cam[1], cam[2]);
        if (Double.isNaN(projected[0]) || Double.isNaN(projected[1])) {
            return null;
        }
        return projected;
    }

    private double[][] toPointMatrix(Object raw, int rows, int cols) {
        if (!(raw instanceof List<?> outer) || outer.size() < rows) {
            return null;
        }
        double[][] matrix = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            Object item = outer.get(row);
            if (!(item instanceof List<?> inner) || inner.size() < cols) {
                return null;
            }
            for (int col = 0; col < cols; col++) {
                matrix[row][col] = toDouble(inner.get(col));
            }
        }
        return matrix;
    }

    private double[][] toFlatPointMatrix(Object raw, int rows, int cols) {
        List<Double> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof List<?> nested) {
                    for (Object nestedValue : nested) {
                        values.add(toDouble(nestedValue));
                    }
                } else {
                    values.add(toDouble(value));
                }
            }
        } else if (raw instanceof String text) {
            for (String token : numericTokens(text)) {
                values.add(Double.parseDouble(token));
            }
        }
        if (values.size() < rows * cols) {
            return null;
        }
        double[][] matrix = new double[rows][cols];
        for (int i = 0; i < rows * cols; i++) {
            matrix[i / cols][i % cols] = values.get(i);
        }
        return matrix;
    }

    private String[] numericTokens(String text) {
        return text.replace('[', ' ')
                .replace(']', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .trim()
                .split("[,\\s]+");
    }

    private Calibration parseCalibration(InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        double[][] p2 = null;
        double[][] r0 = identity3();
        double[][] tr = null;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("P2:")) {
                p2 = parseMatrix(trimmed.substring(3), 3, 4);
            } else if (trimmed.startsWith("R0_rect:")) {
                r0 = parseMatrix(trimmed.substring(8), 3, 3);
            } else if (trimmed.startsWith("Tr_velo_to_cam:")) {
                tr = parseMatrix(trimmed.substring(15), 3, 4);
            }
        }
        if (p2 == null || tr == null) {
            return null;
        }
        return new Calibration(p2, r0, tr);
    }

    private double[][] parseMatrix(String text, int rows, int cols) {
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < rows * cols) {
            return null;
        }
        double[][] matrix = new double[rows][cols];
        for (int i = 0; i < rows * cols; i++) {
            matrix[i / cols][i % cols] = Double.parseDouble(tokens[i]);
        }
        return matrix;
    }

    private double[][] identity3() {
        return new double[][]{
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };
    }

    private static class Calibration {
        private final double[][] p2;
        private final double[][] r0;
        private final double[][] tr;

        private Calibration(double[][] p2, double[][] r0, double[][] tr) {
            this.p2 = p2;
            this.r0 = r0;
            this.tr = tr;
        }

        private double[] lidarToCamera(double x, double y, double z) {
            double[] velo = new double[]{x, y, z, 1.0};
            double[] cam = multiply3x4(tr, velo);
            double[] camRect = multiply3x3(r0, cam);
            return camRect;
        }

        private double[] projectToImage(double x, double y, double z) {
            double[] hom = new double[]{x, y, z, 1.0};
            double u = dotRow(p2[0], hom);
            double v = dotRow(p2[1], hom);
            double w = dotRow(p2[2], hom);
            return new double[]{u / w, v / w};
        }

        private double[] multiply3x4(double[][] matrix, double[] vector4) {
            double[] result = new double[3];
            for (int row = 0; row < 3; row++) {
                result[row] = matrix[row][0] * vector4[0]
                        + matrix[row][1] * vector4[1]
                        + matrix[row][2] * vector4[2]
                        + matrix[row][3] * vector4[3];
            }
            return result;
        }

        private double[] multiply3x3(double[][] matrix, double[] vector3) {
            double[] result = new double[3];
            for (int row = 0; row < 3; row++) {
                result[row] = matrix[row][0] * vector3[0]
                        + matrix[row][1] * vector3[1]
                        + matrix[row][2] * vector3[2];
            }
            return result;
        }

        private double dotRow(double[] row, double[] vector4) {
            return row[0] * vector4[0] + row[1] * vector4[1] + row[2] * vector4[2] + row[3] * vector4[3];
        }
    }

    private record KittiSampleFiles(Path pointCloud, Path calib) {
    }

    private enum BoxCoordinate {
        LIDAR,
        CAMERA
    }

    private record BoxCorners(double[][] points, BoxCoordinate coordinate) {
    }
}
