package org.xoralundra.annuitybackend.core

import com.beust.klaxon.Klaxon
import mu.KotlinLogging
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.Test
import org.xoralundra.annuitybackend.common.PolicyState
import org.xoralundra.annuitybackend.mockapis.MockClock
import org.xoralundra.annuitybackend.mockapis.MockMsgQueues
import org.xoralundra.annuitybackend.mockapis.MsgRecord
import org.xoralundra.annuitybackend.mockapis.ThePolicyStore
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PolicyLifeClycleratorTest {
    private val logger = KotlinLogging.logger {  }

    val cyclerator: PolicyLifeCylerator = PolicyLifeCylerator(
            MockMsgQueues.PolicyEventConsumer,
            MockMsgQueues.BankEventProducer,
            ThePolicyStore,
            MockClock
        )

    val premium1 = Money.of(CurrencyUnit.USD, 5000.00)
    val payout1 = Money.of(CurrencyUnit.USD, 5.00)
    val interestRate1: Double = 5.0
    val cancellationFee1 = Money.of(CurrencyUnit.USD, 500.00)
    val termLength1 = Duration.of(3500, ChronoUnit.DAYS)
    val termLength2 = Duration.of(356, ChronoUnit.DAYS) // Intentionally less than a year

    val annuityInfo1 = AnnuityInfo(
        premium1,
        payout1,
        interestRate1,
        cancellationFee1,
        termLength1
    )

    val annuityInfo2 = AnnuityInfo(
        premium1,
        payout1,
        interestRate1,
        cancellationFee1,
        termLength2
    )
    val policyId0 = "0000000000"
    val policyId1 = UUID.randomUUID().toString()
    val policyId2 = UUID.randomUUID().toString()
    val policyId3 = UUID.randomUUID().toString()

    val insuredTIN3 = "000000002"
    val insuredTIN4 = "000000003"
    val insuredTIN1 = "000000000"
    val insuredTIN2 = "000000001"

    val insuredAddress1 = Address("123 Any Street",
        null, "Any Place", "MA",
        "00000", "US")

    val insured0 = Insured(
        "Fred Jones",
        insuredAddress1,
        "+1-617-555-1210",
        Date(0),//.parse("03/07/70")
        insuredTIN4,
        arrayOf(policyId3)
    )
    val insured1 = Insured(
        "Norville Rogers",
        insuredAddress1,
        "+1-617-555-1212",
        Date(0),//.parse("03/07/70")
        insuredTIN1,
        arrayOf(policyId1)
    )
    val insured2 = Insured(
        "Daphne Blaine",
        insuredAddress1,
        "+1-617-555-1213",
        Date(0),//.parse("03/07/70")
        insuredTIN2,
        arrayOf(policyId0)
    )
    val insured3 = Insured(
        "Velma Dinkley",
        insuredAddress1,
        "+1-617-555-1214",
        Date(0),//.parse("03/07/70")
        insuredTIN3,
        arrayOf(policyId2)
    )

    val applicationId0 = "0000000000"


    @Test
    fun testBasicFunctions() {
        MockClock.reset()

        val application = Application(insured1, annuityInfo1, applicationId0)
        application.verifiedBankAccount(true)
        application.verifiedFunds(premium1)
        application.verifiedIsNotMoneyLaunderer(true)
        val policy = cyclerator.createPolicy(application, policyId1)
//        ThePolicyStore.savePolicy(policy)

        val premiumPaid = Money.of(CurrencyUnit.USD, 5000.00)
        val payment = Payment(premiumPaid, MockClock.now, policyId1)

        MockMsgQueues.enqueuePolicyEvent("premium", payment)
        cyclerator.processPolicies(exit = true)

        //System.err.println("policy state is ${policy.policyState}.")
        logger.info { "policy state is ${policy.policyState}." }
        assertEquals(PolicyState.CANCELLABLE, policy.policyState)
        assertNull(MockMsgQueues.pollBankEvent())

        val cancelleation = Cancellation(policyId1, MockClock.now)
        MockMsgQueues.enqueuePolicyEvent("cancel", cancelleation)
        cyclerator.processPolicies(exit = true)
        assertEquals(PolicyState.CANCELLED, policy.policyState)
        val payoutRecord: MsgRecord<String, String>? = MockMsgQueues.pollBankEvent()
        assertNotNull(payoutRecord)
        assertEquals("REFUND", payoutRecord.key)
        val actualPayout = Klaxon().converter(sumConverter).parse<Payout>(payoutRecord.value)
        assertEquals(premium1, actualPayout?.sum)
    }

    @Test
    fun testCancellableWindow() {
        MockClock.reset()

        val application = Application(insured2, annuityInfo1, applicationId0)
        application.verifiedBankAccount(true)
        application.verifiedFunds(premium1)
        application.verifiedIsNotMoneyLaunderer(true)
        val policy = cyclerator.createPolicy(application, policyId0)
//        ThePolicyStore.savePolicy(policy)

        val premiumPaid = Money.of(CurrencyUnit.USD, 5000.00)
        val payment = Payment(premiumPaid, MockClock.now, policyId0)

        MockMsgQueues.enqueuePolicyEvent("premium", payment)
        cyclerator.processPolicies(exit = true)

        //System.err.println("policy state is ${policy.policyState}.")
        logger.info { "policy state is ${policy.policyState}." }
        assertEquals(PolicyState.CANCELLABLE, policy.policyState)
        assertNull(MockMsgQueues.pollBankEvent())

        MockClock.instant = MockClock.now.plusMonths(1L).toInstant()

        val clockTick = PolicyUpdate(policyId0, MockClock.now)
        MockMsgQueues.enqueuePolicyEvent("tick", clockTick)
        cyclerator.processPolicies(exit = true)
        val firstPayoutRecord = MockMsgQueues.pollBankEvent()
        assertNotNull(firstPayoutRecord)
        assertEquals("PAYMENT", firstPayoutRecord.key)
        val firstPayout = Klaxon().converter(sumConverter).parse<Payout>(firstPayoutRecord.value)
        assertEquals(payout1, firstPayout?.sum)
        assertEquals(PolicyState.ACTIVE, policy.policyState)

        val cancellation = Cancellation(policyId0, MockClock.now)
        MockMsgQueues.enqueuePolicyEvent("cancel", cancellation)
        cyclerator.processPolicies(exit = true)
        assertEquals(PolicyState.CANCELLED, policy.policyState)
        val payoutRecord: MsgRecord<String, String>? = MockMsgQueues.pollBankEvent()
        assertNotNull(payoutRecord)
        assertEquals("REFUND", payoutRecord.key)
        val actualPayout = Klaxon().converter(sumConverter).parse<Payout>(payoutRecord.value)
        assertEquals(premium1?.minus(cancellationFee1)?.minus(payout1)?.plus(20.83), actualPayout?.sum)
    }

    @Test
    fun testOwnerDeath() {
        MockClock.reset()

        val application = Application(insured3, annuityInfo1, applicationId0)
        application.verifiedBankAccount(true)
        application.verifiedFunds(premium1)
        application.verifiedIsNotMoneyLaunderer(true)
        val policy = cyclerator.createPolicy(application, policyId2)

        val premiumPaid = Money.of(CurrencyUnit.USD, 5000.00)
        val payment = Payment(premiumPaid, MockClock.now, policyId2)

        MockMsgQueues.enqueuePolicyEvent("premium", payment)
        cyclerator.processPolicies(exit = true)

        //System.err.println("policy state is ${policy.policyState}.")
        logger.info { "policy state is ${policy.policyState}." }
        assertEquals(PolicyState.CANCELLABLE, policy.policyState)
        assertNull(MockMsgQueues.pollBankEvent())

        MockClock.instant = MockClock.now.plusMonths(1L).toInstant()

        val clockTick1 = PolicyUpdate(policyId2, MockClock.now)
        MockMsgQueues.enqueuePolicyEvent("tick", clockTick1)
        cyclerator.processPolicies(exit = true)
        val firstPayoutRecord = MockMsgQueues.pollBankEvent()
        assertNotNull(firstPayoutRecord)
        assertEquals(firstPayoutRecord.key, "PAYMENT")
        val firstPayout = Klaxon().converter(sumConverter).parse<Payout>(firstPayoutRecord.value)
        assertEquals(payout1, firstPayout?.sum)
        assertEquals(PolicyState.ACTIVE, policy.policyState)

        MockClock.instant = MockClock.now.plusMonths(1L).toInstant()

        val clockTick2 = PolicyUpdate(policyId2, MockClock.now)
        MockMsgQueues.enqueuePolicyEvent("tick", clockTick2)
        cyclerator.processPolicies(exit = true)
        val secondPayoutRecord = MockMsgQueues.pollBankEvent()
        assertNotNull(secondPayoutRecord)
        assertEquals("PAYMENT", secondPayoutRecord.key)
        val secondPayout = Klaxon().converter(sumConverter).parse<Payout>(secondPayoutRecord.value)
        assertEquals(payout1, secondPayout?.sum)
        assertEquals(PolicyState.ACTIVE, policy.policyState)

        val ownerDeath = Death(MockClock.now, insuredTIN3)
        MockMsgQueues.enqueuePolicyEvent("death", ownerDeath)
        cyclerator.processPolicies(exit = true)
        assertEquals(PolicyState.RIP, policy.policyState)
        val payoutRecord: MsgRecord<String, String>? = MockMsgQueues.pollBankEvent()
        assertNotNull(payoutRecord)
        assertEquals("TO_BENEFICIARIES", payoutRecord.key)
        val actualPayout = Klaxon().converter(sumConverter).parse<Payout>(payoutRecord.value)
        assertEquals(premium1?.minus(payout1)?.plus(36.73), actualPayout?.sum)
    }

    @Test
    fun testPolicyMaturity() {
        MockClock.reset()

        val application = Application(insured0, annuityInfo2, applicationId0)
        application.verifiedBankAccount(true)
        application.verifiedFunds(premium1)
        application.verifiedIsNotMoneyLaunderer(true)
        val policy = cyclerator.createPolicy(application, policyId3)

        val premiumPaid = Money.of(CurrencyUnit.USD, 5000.00)
        val payment = Payment(premiumPaid, MockClock.now, policyId3)

        MockMsgQueues.enqueuePolicyEvent("premium", payment)
        cyclerator.processPolicies(exit = true)

        //System.err.println("policy state is ${policy.policyState}.")
        logger.info { "policy state is ${policy.policyState}." }
        assertEquals(PolicyState.CANCELLABLE, policy.policyState)
        assertNull(MockMsgQueues.pollBankEvent())

        for (month in 0..10) {
            logger.info { "testPolicyMaturity month $month" }
            MockClock.instant = MockClock.now.plusMonths(1L).toInstant()

            val clockTick = PolicyUpdate(policyId3, MockClock.now)
            MockMsgQueues.enqueuePolicyEvent("tick", clockTick)
            cyclerator.processPolicies(exit = true)
            val firstPayoutRecord = MockMsgQueues.pollBankEvent()
            assertNotNull(firstPayoutRecord)
            val firstPayout = Klaxon().converter(sumConverter).parse<Payout>(firstPayoutRecord.value)
            assertEquals(payout1, firstPayout?.sum)
            assertEquals(PolicyState.ACTIVE, policy.policyState)
        }

        MockClock.instant = MockClock.now.plusMonths(1L).toInstant()

        val clockTick2 = PolicyUpdate(policyId3, MockClock.now)
        MockMsgQueues.enqueuePolicyEvent("tick", clockTick2)
        cyclerator.processPolicies(exit = true)
        assertEquals(PolicyState.COMPLETED, policy.policyState)
        val lastPayoutRecord = MockMsgQueues.pollBankEvent()
        assertNotNull(lastPayoutRecord)
        assertEquals("TERM_COMPLETE", lastPayoutRecord.key)
        val lastPayout = Klaxon().converter(sumConverter).parse<Payout>(lastPayoutRecord.value)
        assertEquals(premium1.plus(199.42), lastPayout?.sum)
     }
        //==
}