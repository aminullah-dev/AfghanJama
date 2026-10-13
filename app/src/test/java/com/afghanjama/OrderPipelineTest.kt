package com.afghanjama

import com.afghanjama.data.OrderPipeline
import com.afghanjama.data.OrderPipeline.State
import com.afghanjama.data.entities.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نگاشتِ هشت حالتِ [OrderStatus] به پنج قدمِ چرخه، و حالتِ هر قدم.
 */
class OrderPipelineTest {

    private fun states(status: String) = OrderPipeline.steps(status).map { it.state }

    @Test
    fun `five stages, always`() {
        assertEquals(5, OrderPipeline.STAGES.size)
        assertEquals(5, OrderPipeline.steps(OrderStatus.SEWING.name).size)
    }

    @Test
    fun `cutting sits on the second step as current, first done`() {
        assertEquals(1, OrderPipeline.indexOf(OrderStatus.CUTTING.name))
        assertEquals(
            listOf(State.DONE, State.CURRENT, State.UPCOMING, State.UPCOMING, State.UPCOMING),
            states(OrderStatus.CUTTING.name),
        )
    }

    @Test
    fun `cut done is still the cutting step, not sewing`() {
        assertEquals(1, OrderPipeline.indexOf(OrderStatus.CUT_DONE.name))
        assertEquals(State.CURRENT, OrderPipeline.steps(OrderStatus.CUT_DONE.name)[1].state)
    }

    @Test
    fun `sewing is the middle step`() {
        assertEquals(2, OrderPipeline.indexOf(OrderStatus.SEWING.name))
        assertEquals(
            listOf(State.DONE, State.DONE, State.CURRENT, State.UPCOMING, State.UPCOMING),
            states(OrderStatus.SEWING.name),
        )
    }

    @Test
    fun `sent completes every step, nothing is current`() {
        assertEquals(4, OrderPipeline.indexOf(OrderStatus.SENT.name))
        assertTrue(OrderPipeline.isComplete(OrderStatus.SENT.name))
        assertEquals(List(5) { State.DONE }, states(OrderStatus.SENT.name))
    }

    @Test
    fun `stored is ready, not delivered — last step stays current`() {
        assertEquals(4, OrderPipeline.indexOf(OrderStatus.STORED.name))
        assertEquals(
            listOf(State.DONE, State.DONE, State.DONE, State.DONE, State.CURRENT),
            states(OrderStatus.STORED.name),
        )
    }

    @Test
    fun `a brand-new order starts on the first step`() {
        assertEquals(0, OrderPipeline.indexOf("NEW"))
        assertEquals(State.CURRENT, OrderPipeline.steps("NEW")[0].state)
        assertEquals(0, OrderPipeline.indexOf(OrderStatus.IN_STOCK.name))
    }

    @Test
    fun `an unknown status greens nothing`() {
        assertEquals(-1, OrderPipeline.indexOf("WAT"))
        assertEquals(List(5) { State.UPCOMING }, states("WAT"))
    }
}
