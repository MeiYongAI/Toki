package io.github.meiyongai.toki.hook

/** 扫描和缓存发布的有序阶段；完成结果等待用户确认与手动重启。 */
internal enum class HostScanPhase { IDLE, SCANNING, SAVING, READY, FAILED }

/** 单次发布的不可变进度；单位表示已处理工作，不表示预计耗时。 */
internal data class HostScanStatus(
    val phase: HostScanPhase = HostScanPhase.IDLE,
    val completed: Int = 0,
    val total: Int = 0
)

/** 统一约束扫描、保存与结果确认顺序，供工作线程和主线程共同使用。 */
internal class HostScanSession {
    @Volatile var status = HostScanStatus()
        private set

    /** 开始唯一扫描。@return Unit。Callers: HostSymbols.initialize、单元测试。 */
    @Synchronized fun begin() {
        check(status.phase == HostScanPhase.IDLE)
        status = HostScanStatus(HostScanPhase.SCANNING)
    }

    /**
     * 发布单调递增的实际扫描工作量。
     * @param completed 已完成的工作单位。
     * @param total 此次扫描总单位。
     * @return Unit；不合法或倒退的进度明确报告。
     * Callers: HostDexIndex进度回调、单元测试。
     */
    @Synchronized fun progress(completed: Int, total: Int) {
        check(status.phase == HostScanPhase.SCANNING)
        require(total > 0 && completed in status.completed..total)
        require(status.total == 0 || status.total == total)
        status = HostScanStatus(HostScanPhase.SCANNING, completed, total)
    }

    /** 扫描全部完成后开始保存结果。@return Unit。Callers: HostSymbols、单元测试。 */
    @Synchronized fun saving() {
        check(status.phase == HostScanPhase.SCANNING && status.total > 0 && status.completed == status.total)
        status = status.copy(phase = HostScanPhase.SAVING)
    }

    /**
     * 在缓存原子保存成功且重新读取核对一致后发布完成提示。
     * @return Unit。
     * Callers: HostSymbols、单元测试。
     */
    @Synchronized fun ready() {
        check(status.phase == HostScanPhase.SAVING)
        status = status.copy(phase = HostScanPhase.READY)
    }

    /**
     * 发布失败状态；具体诊断写入功能日志。
     * @return Unit。
     * Callers: HostSymbols、HostScanController、单元测试。
     */
    @Synchronized fun fail() { status = status.copy(phase = HostScanPhase.FAILED) }

    /** 仅确认完成或失败提示，不改变缓存和宿主进程。@return Unit。Callers: HostScanController、单元测试。 */
    @Synchronized fun dismissResult() {
        check(status.phase == HostScanPhase.READY || status.phase == HostScanPhase.FAILED)
        status = HostScanStatus()
    }
}
