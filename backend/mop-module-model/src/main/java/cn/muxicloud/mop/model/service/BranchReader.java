package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchViewCache;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.entity.ElementRefEntity;
import cn.muxicloud.mop.model.mapper.BranchMapper;
import cn.muxicloud.mop.model.mapper.ElementMapper;
import cn.muxicloud.mop.model.mapper.ElementRefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 沿父分支链读取视图。当前分支的行覆盖祖先，不复制元素。链深超过 5 层时拒绝读取。
 */
@Service
public class BranchReader {
    static final int MAX_DEPTH = 5;

    private final BranchMapper branchMapper;
    private final ElementMapper elementMapper;
    private final ElementRefMapper elementRefMapper;
    private final BranchViewCache cache;
    private final ThreadLocal<Long> readingRev = new ThreadLocal<>();

    public BranchReader(BranchMapper branchMapper, ElementMapper elementMapper, ElementRefMapper elementRefMapper, BranchViewCache cache) {
        this.branchMapper = branchMapper;
        this.elementMapper = elementMapper;
        this.elementRefMapper = elementRefMapper;
        this.cache = cache;
    }

    public void reading(long rev) {
        readingRev.set(rev);
    }

    public void clearReading() {
        readingRev.remove();
    }

    public List<ElementEntity> view(long branchId) {
        BranchEntity branch = required(branchId);
        Long override = readingRev.get();
        if (override != null) {
            return compute(branch, override);
        }
        long rev = branch.getHeadRev() == null ? 0L : branch.getHeadRev();
        return cached(branch, rev);
    }

    public List<ElementEntity> viewAt(long branchId, long rev) {
        BranchEntity branch = required(branchId);
        if (rev < 0 || (branch.getHeadRev() != null && rev > branch.getHeadRev())) {
            throw new MopException(ErrorCode.VALIDATION, "修订号超出该分支", null);
        }
        return cached(branch, rev);
    }

    public ElementEntity find(long branchId, String uid) {
        if (uid == null || uid.isBlank()) {
            return null;
        }
        for (ElementEntity row : view(branchId)) {
            if (uid.equals(row.getUid())) {
                return row;
            }
        }
        return null;
    }

    public List<ElementRefEntity> refsTo(long branchId, String uid) {
        List<ElementRefEntity> hits = new ArrayList<>();
        for (ElementEntity element : view(branchId)) {
            if (uid.equals(element.getUid())) {
                continue;
            }
            for (ElementRefEntity ref : refsOf(element)) {
                if (uid.equals(ref.getToUid())) {
                    hits.add(ref);
                }
            }
        }
        return hits;
    }

    private List<ElementEntity> cached(BranchEntity branch, long rev) {
        String key = branch.getProjectId() + ":" + branch.getId() + ":" + rev;
        List<ElementEntity> hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        List<ElementEntity> loaded = compute(branch, rev);
        cache.put(key, loaded);
        return loaded;
    }

    private List<ElementEntity> compute(BranchEntity branch, long rev) {
        List<Hop> chain = chain(branch, rev);
        Map<String, ElementEntity> overlay = new LinkedHashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            Hop hop = chain.get(i);
            Map<String, ElementEntity> best = new LinkedHashMap<>();
            for (ElementEntity row : rowsAt(hop)) {
                ElementEntity previous = best.get(row.getUid());
                if (previous == null || row.getRevStart() >= previous.getRevStart()) {
                    best.put(row.getUid(), row);
                }
            }
            for (ElementEntity row : best.values()) {
                if (row.getTombstone() != null && row.getTombstone() == 1) {
                    overlay.remove(row.getUid());
                } else {
                    overlay.put(row.getUid(), row);
                }
            }
        }
        return List.copyOf(overlay.values());
    }

    private List<Hop> chain(BranchEntity start, long cutoff) {
        List<Hop> hops = new ArrayList<>();
        BranchEntity current = start;
        while (current != null) {
            if (hops.size() >= MAX_DEPTH) {
                throw new MopException(ErrorCode.VALIDATION, "分支链超过 5 层，请先合并或变基", null);
            }
            hops.add(new Hop(current.getId(), cutoff));
            if (current.getParentBranchId() == null) {
                break;
            }
            BranchEntity parent = branchMapper.selectById(current.getParentBranchId());
            cutoff = current.getForkRev() == null ? 0L : current.getForkRev();
            current = parent;
        }
        return hops;
    }

    private List<ElementEntity> rowsAt(Hop hop) {
        return elementMapper.selectList(new LambdaQueryWrapper<ElementEntity>()
                .eq(ElementEntity::getBranchId, hop.branchId())
                .le(ElementEntity::getRevStart, hop.cutoff())
                .and(query -> query.isNull(ElementEntity::getRevEnd).or().gt(ElementEntity::getRevEnd, hop.cutoff())));
    }

    private List<ElementRefEntity> refsOf(ElementEntity element) {
        LambdaQueryWrapper<ElementRefEntity> query = new LambdaQueryWrapper<ElementRefEntity>()
                .eq(ElementRefEntity::getBranchId, element.getBranchId())
                .eq(ElementRefEntity::getFromUid, element.getUid())
                .eq(ElementRefEntity::getRevStart, element.getRevStart());
        if (element.getRevEnd() == null) {
            query.isNull(ElementRefEntity::getRevEnd);
        } else {
            Long closedAt = element.getRevEnd();
            query.and(wrapper -> wrapper.isNull(ElementRefEntity::getRevEnd).or().ge(ElementRefEntity::getRevEnd, closedAt));
        }
        return elementRefMapper.selectList(query);
    }

    private BranchEntity required(long branchId) {
        BranchEntity branch = branchMapper.selectById(branchId);
        if (branch == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return branch;
    }

    private record Hop(long branchId, long cutoff) {}
}
