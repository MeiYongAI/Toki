package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 验证扫描缓存提交和用户确认提示的完整顺序。 */
class HostScanSessionTest {
    /** 保存成功后保留完成摘要，直到用户确认提示。@return Unit。Callers: JUnit。 */
    @Test fun savedResultWaitsForUserAcknowledgement() {
        val session = HostScanSession()
        session.begin()
        session.progress(0, 100)
        session.progress(100, 100)
        session.saving()
        assertEquals(HostScanPhase.SAVING, session.status.phase)
        session.ready()
        assertEquals(HostScanPhase.READY, session.status.phase)
        assertEquals(100, session.status.completed)
        session.dismissResult()
        assertEquals(HostScanPhase.IDLE, session.status.phase)
    }

    /** 失败原因保留到用户确认，确认不伪造扫描成功。@return Unit。Callers: JUnit。 */
    @Test fun failureCanBeAcknowledgedWithoutPublishingSuccess() {
        val session = HostScanSession()
        session.begin()
        session.progress(10, 10)
        session.saving()
        session.fail()
        assertEquals(HostScanPhase.FAILED, session.status.phase)
        session.dismissResult()
        assertEquals(HostScanPhase.IDLE, session.status.phase)
    }

    /** 查找和保存尚未完成时不能确认结果。@return Unit。Callers: JUnit。 */
    @Test fun unfinishedScanCannotBeDismissed() {
        val session = HostScanSession()
        session.begin()
        assertThrows(IllegalStateException::class.java) { session.dismissResult() }
        session.progress(1, 1)
        session.saving()
        assertThrows(IllegalStateException::class.java) { session.dismissResult() }
        assertEquals(HostScanPhase.SAVING, session.status.phase)
    }

    /** 验证进度倒退明确失败，不显示虚构进度。@return Unit。Callers: JUnit。 */
    @Test(expected = IllegalArgumentException::class) fun backwardProgressIsRejected() {
        val session = HostScanSession()
        session.begin()
        session.progress(20, 100)
        session.progress(10, 100)
    }

    /** 验证扫描未结束时不能进入缓存提交阶段。@return Unit。Callers: JUnit。 */
    @Test(expected = IllegalStateException::class) fun incompleteScanCannotBeSaved() {
        val session = HostScanSession()
        session.begin()
        session.progress(99, 100)
        session.saving()
    }
}
