package com.greendynasty.football.data.importer

/**
 * 数据导入异常基类
 *
 * 用于统一标识导入流程中解析失败、校验失败、IO 错误等异常情况。
 * 所有 parser/validator/manifest 模块在不可恢复的错误场景下抛出此异常。
 */
class ImportException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable?) : super(message, cause)
}
