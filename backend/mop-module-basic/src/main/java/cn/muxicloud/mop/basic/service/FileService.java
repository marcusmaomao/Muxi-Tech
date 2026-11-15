package cn.muxicloud.mop.basic.service;

import cn.muxicloud.mop.basic.api.DocumentText;
import cn.muxicloud.mop.basic.entity.StoredFileEntity;
import cn.muxicloud.mop.basic.mapper.StoredFileMapper;
import cn.muxicloud.mop.basic.parse.TextExtractor;
import cn.muxicloud.mop.basic.storage.ObjectStore;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.common.id.Ulids;
import cn.muxicloud.mop.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.OutputStream;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService implements DocumentText {
    static final long MAX_BYTES = 50L * 1024 * 1024;

    private final StoredFileMapper fileMapper;
    private final ObjectStore objectStore;
    private final TextExtractor textExtractor;

    public FileService(StoredFileMapper fileMapper, ObjectStore objectStore, TextExtractor textExtractor) {
        this.fileMapper = fileMapper;
        this.objectStore = objectStore;
        this.textExtractor = textExtractor;
    }

    public List<FileView> list() {
        return fileMapper.selectList(new LambdaQueryWrapper<StoredFileEntity>().orderByDesc(StoredFileEntity::getId))
                .stream().map(FileView::from).toList();
    }

    public FileView upload(MultipartFile file, Long projectId, String category, AuthenticatedUser actor) {
        if (file == null || file.isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, "请选择文件", null);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new MopException(ErrorCode.FILE_TOO_LARGE);
        }
        String name = filename(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank() ? "application/octet-stream" : file.getContentType();
        if (category != null && category.trim().length() > 64) {
            throw new MopException(ErrorCode.VALIDATION, "分类最长 64", null);
        }
        byte[] body;
        try {
            body = file.getBytes();
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "文件读取失败", null);
        }
        TextExtractor.Extracted extracted = textExtractor.extract(body, name);
        String key = actor.tenantId() + "/" + Ulids.newUlid() + "/" + name;
        objectStore.put(key, body, contentType);
        StoredFileEntity entity = new StoredFileEntity();
        entity.setTenantId(actor.tenantId());
        entity.setProjectId(projectId);
        entity.setCategory(category == null || category.isBlank() ? null : category.trim());
        entity.setOriginalName(name);
        entity.setContentType(contentType);
        entity.setSizeBytes(file.getSize());
        entity.setObjectKey(key);
        entity.setTextStatus(extracted.status());
        entity.setExtractedText(extracted.text());
        entity.setCreatedBy(actor.userId());
        fileMapper.insert(entity);
        return FileView.from(entity);
    }

    public void preview(Long id, OutputStream out) {
        objectStore.writeTo(required(id).getObjectKey(), out);
    }

    public StoredFileEntity required(Long id) {
        StoredFileEntity entity = fileMapper.selectById(id);
        if (entity == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    @Override
    public Extracted read(long fileId) {
        StoredFileEntity entity = required(fileId);
        String stored = entity.getExtractedText() == null ? "" : entity.getExtractedText();
        TextExtractor.Extracted again = textExtractor.extract(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8), entity.getOriginalName());
        String text = "OK".equals(again.status()) && again.text() != null && !again.text().isBlank() ? again.text() : stored;
        if (text.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "文件没有可用文本", null);
        }
        return new Extracted(entity.getId(), entity.getOriginalName(), text.trim());
    }

    public TextView text(Long id) {
        StoredFileEntity entity = required(id);
        return new TextView(entity.getId(), entity.getOriginalName(), entity.getTextStatus(), entity.getExtractedText());
    }

    private static String filename(String original) {
        String name = original == null ? "file" : original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        name = slash >= 0 ? name.substring(slash + 1) : name;
        name = name.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]", "_");
        if (name.isBlank()) {
            name = "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    public record FileView(Long id, Long projectId, String category, String originalName, String contentType, Long sizeBytes, String textStatus) {
        static FileView from(StoredFileEntity entity) {
            return new FileView(entity.getId(), entity.getProjectId(), entity.getCategory(), entity.getOriginalName(), entity.getContentType(), entity.getSizeBytes(), entity.getTextStatus());
        }
    }

    public record TextView(Long id, String originalName, String textStatus, String text) {}
}
