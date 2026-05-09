package com.chat.service;

import com.chat.config.FileUploadConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileUploadService {

    @Autowired
    private FileUploadConfig config;

    private Path basePath;

    @PostConstruct
    public void init() throws IOException {
        basePath = Paths.get(config.getBasePath()).toAbsolutePath();
        if (!Files.exists(basePath)) {
            Files.createDirectories(basePath);
        }
    }

    public FileUploadResult upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);

        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }

        if (file.getSize() > config.getMaxSize() * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小超出限制: " + config.getMaxSize() + "MB");
        }

        String category = getFileCategory(extension);
        String datePath = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(new Date());

        Path categoryPath = basePath.resolve(category).resolve(datePath);
        Files.createDirectories(categoryPath);

        String newFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path filePath = categoryPath.resolve(newFilename);

        byte[] bytes = file.getBytes();
        Files.write(filePath, bytes);

        String relativePath = category + "/" + datePath + "/" + newFilename;
        String accessUrl = "/uploads/" + relativePath;

        return new FileUploadResult(
            newFilename,
            originalFilename,
            extension,
            file.getSize(),
            accessUrl,
            filePath.toString()
        );
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.CHINA);
    }

    private boolean isAllowedExtension(String extension) {
        if (config.getAllowedExtensions() == null) {
            return true;
        }
        return config.getAllowedExtensions().contains(extension.toLowerCase(Locale.CHINA));
    }

    private String getFileCategory(String extension) {
        if (extension == null) {
            return "other";
        }
        switch (extension.toLowerCase(Locale.CHINA)) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "webp":
                return "image";
            case "mp4":
            case "avi":
            case "mov":
            case "wmv":
            case "mkv":
                return "video";
            case "mp3":
            case "wav":
            case "ogg":
            case "flac":
                return "audio";
            case "pdf":
                return "document";
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
                return "office";
            case "txt":
            case "json":
            case "xml":
            case "log":
                return "text";
            default:
                return "other";
        }
    }

    public static class FileUploadResult {
        private String filename;
        private String originalName;
        private String extension;
        private long size;
        private String url;
        private String path;

        public FileUploadResult(String filename, String originalName, String extension, 
                               long size, String url, String path) {
            this.filename = filename;
            this.originalName = originalName;
            this.extension = extension;
            this.size = size;
            this.url = url;
            this.path = path;
        }

        public String getFilename() { return filename; }
        public String getOriginalName() { return originalName; }
        public String getExtension() { return extension; }
        public long getSize() { return size; }
        public String getUrl() { return url; }
        public String getPath() { return path; }
    }

    /**
     * 获取文件信息
     */
    public FileInfo getFileInfo(String relativePath) throws IOException {
        Path filePath = basePath.resolve(relativePath);
        
        if (!Files.exists(filePath)) {
            return null;
        }
        
        String filename = filePath.getFileName().toString();
        long size = Files.size(filePath);
        String contentType = Files.probeContentType(filePath);
        
        return new FileInfo(filename, size, contentType, filePath.toString());
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String relativePath) {
        Path filePath = basePath.resolve(relativePath);
        return Files.exists(filePath);
    }

    public static class FileInfo {
        private String filename;
        private long size;
        private String contentType;
        private String path;

        public FileInfo(String filename, long size, String contentType, String path) {
            this.filename = filename;
            this.size = size;
            this.contentType = contentType;
            this.path = path;
        }

        public String getFilename() { return filename; }
        public long getSize() { return size; }
        public String getContentType() { return contentType; }
        public String getPath() { return path; }
    }
}