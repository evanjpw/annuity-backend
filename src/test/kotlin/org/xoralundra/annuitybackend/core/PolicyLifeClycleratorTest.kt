package org.xoralundra.annuitybackend.core

import org.junit.*
import org.junit.Test
import org.xoralundra.annuitybackend.mockapis.MockClock
import org.xoralundra.annuitybackend.mockapis.MockMsgQueues
import org.xoralundra.annuitybackend.mockapis.ThePolicyStore
import kotlin.test.*

class PolicyLifeClycleratorTest {
    @Test
    fun testBasicFunctions() {
        val cyclerator = PolicyLifeCylerator(MockMsgQueues.PolicyEventConsumer,
            MockMsgQueues.BankEventProducer,
            ThePolicyStore,
            MockClock
        )
        cyclerator.processPolicies(exit = true)

    }
}