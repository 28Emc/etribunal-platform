package com.etribunal.core.cases;

import java.util.Objects;
import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pageable con offset absoluto para replicar la paginación skip/take del monolito.
 */
public final class OffsetPageable extends AbstractPageRequest {

    private final long offset;
    private final Sort sort;

    public OffsetPageable(int skip, int limit, Sort sort) {
        super(skip / limit, limit);
        this.offset = skip;
        this.sort = sort;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public OffsetPageable next() {
        return new OffsetPageable((int) (offset + getPageSize()), getPageSize(), sort);
    }

    @Override
    public OffsetPageable previous() {
        long previousOffset = Math.max(0, offset - getPageSize());
        return new OffsetPageable((int) previousOffset, getPageSize(), sort);
    }

    @Override
    public OffsetPageable first() {
        return new OffsetPageable(0, getPageSize(), sort);
    }

    @Override
    public Pageable withPage(int page) {
        return new OffsetPageable(page * getPageSize(), getPageSize(), sort);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OffsetPageable that)) return false;
        if (!super.equals(o)) return false;
        return offset == that.offset && Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), offset, sort);
    }
}
