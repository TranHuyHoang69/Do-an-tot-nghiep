package com.example.moneymatev2.domain.model
/**
 * Khóa định danh của 1 category dùng để gộp nhóm / so khớp xuyên suốt app.
 * Category mặc định có stableId (nhãn ngữ nghĩa, tra dịch qua strings.xml).
 * Category do user tự tạo không có stableId -> fallback về id (localId).
 * category == null -> trả về null (nơi gọi tự quyết định xử lý).
 */
fun CategoryModel?.categoryIdentityKey(): String? = this?.stableId ?: this?.id

fun CategoryModel?.matchesIdentity(key: String?): Boolean{
    if(key == null) return this == null
    return categoryIdentityKey() == key
}