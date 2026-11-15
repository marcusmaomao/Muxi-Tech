package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.CanvasViewEntity;
import cn.muxicloud.mop.model.entity.EditLockEntity;
import cn.muxicloud.mop.model.mapper.BranchMapper;
import cn.muxicloud.mop.model.mapper.CanvasViewMapper;
import cn.muxicloud.mop.model.mapper.EditLockMapper;
import cn.muxicloud.mop.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanvasService {
    private final BranchMapper branchMapper;
    private final CanvasViewMapper viewMapper;
    private final EditLockMapper lockMapper;

    public CanvasService(BranchMapper branchMapper, CanvasViewMapper viewMapper, EditLockMapper lockMapper) {
        this.branchMapper = branchMapper;
        this.viewMapper = viewMapper;
        this.lockMapper = lockMapper;
    }

    public List<CanvasViewEntity> list(long branchId) {
        requireBranch(branchId);
        return viewMapper.selectList(new LambdaQueryWrapper<CanvasViewEntity>()
                .eq(CanvasViewEntity::getBranchId, branchId)
                .orderByAsc(CanvasViewEntity::getId));
    }

    @Transactional
    public CanvasViewEntity save(long branchId, String name, String layout, AuthenticatedUser actor) {
        BranchEntity branch = requireBranch(branchId);
        String clean = name == null ? "" : name.trim();
        if (clean.isBlank() || clean.length() > 128) {
            throw new MopException(ErrorCode.VALIDATION, "视图名不能为空，且最长 128", null);
        }
        if (layout == null || layout.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "布局不能为空", null);
        }
        CanvasViewEntity existing = viewMapper.selectOne(new LambdaQueryWrapper<CanvasViewEntity>()
                .eq(CanvasViewEntity::getBranchId, branchId)
                .eq(CanvasViewEntity::getName, clean)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new CanvasViewEntity();
            existing.setTenantId(actor.tenantId());
            existing.setProjectId(branch.getProjectId());
            existing.setBranchId(branchId);
            existing.setName(clean);
            existing.setLayout(layout);
            existing.setUpdatedBy(actor.userId());
            existing.setUpdatedAt(LocalDateTime.now());
            viewMapper.insert(existing);
            return existing;
        }
        existing.setLayout(layout);
        existing.setUpdatedBy(actor.userId());
        existing.setUpdatedAt(LocalDateTime.now());
        viewMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public LockView lock(long branchId, String uid, AuthenticatedUser actor) {
        BranchEntity branch = requireBranch(branchId);
        if (uid == null || !uid.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new MopException(ErrorCode.VALIDATION, "只能锁定已提交的元素", null);
        }
        EditLockEntity row = lockMapper.selectOne(new LambdaQueryWrapper<EditLockEntity>()
                .eq(EditLockEntity::getBranchId, branchId)
                .eq(EditLockEntity::getUid, uid)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (row != null && row.getExpiresAt().isAfter(now) && !row.getUserId().equals(actor.userId())) {
            throw new MopException(ErrorCode.VALIDATION, "该元素正在被他人编辑", null);
        }
        if (row == null) {
            row = new EditLockEntity();
            row.setTenantId(actor.tenantId());
            row.setProjectId(branch.getProjectId());
            row.setBranchId(branchId);
            row.setUid(uid);
            row.setUserId(actor.userId());
            row.setExpiresAt(now.plusMinutes(2));
            lockMapper.insert(row);
        } else {
            row.setUserId(actor.userId());
            row.setExpiresAt(now.plusMinutes(2));
            lockMapper.updateById(row);
        }
        return new LockView(uid, actor.username(), actor.displayName(), true);
    }

    @Transactional
    public void unlock(long branchId, String uid, AuthenticatedUser actor) {
        requireBranch(branchId);
        EditLockEntity row = lockMapper.selectOne(new LambdaQueryWrapper<EditLockEntity>()
                .eq(EditLockEntity::getBranchId, branchId)
                .eq(EditLockEntity::getUid, uid)
                .last("LIMIT 1"));
        if (row != null && row.getUserId().equals(actor.userId())) {
            lockMapper.deleteById(row.getId());
        }
    }

    private BranchEntity requireBranch(long branchId) {
        BranchEntity branch = branchMapper.selectById(branchId);
        if (branch == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return branch;
    }

    public record LockView(String uid, String username, String displayName, boolean mine) {}
}
