package com.matriz.shared;

import java.util.List;

/** A page of a list; {@code totalItems} counts the whole filtered set, not only this page. */
public record PageResponse<T>(List<T> items, int page, int pageSize, long totalItems) {

	public static <T> PageResponse<T> of(List<T> items, Paging paging, long totalItems) {
		return new PageResponse<>(items, paging.page(), paging.pageSize(), totalItems);
	}

}
