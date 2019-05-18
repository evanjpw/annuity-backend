package org.xoralundra.annuitybackend.core

import com.beust.klaxon.Klaxon
import org.xoralundra.annuitybackend.common.ApplicationState
import org.xoralundra.annuitybackend.mockapis.*
import java.time.Clock
import java.time.ZonedDateTime

fun main() {
    val cyclerator = PolicyLifeCylerator(MockMsgQueues.PolicyEventConsumer,
        MockMsgQueues.BankEventProducer,
        ThePolicyStore,
        MockClock)
    cyclerator.processPolicies()
}

class UnapprovedApplicationException(s: String? = null) : Exception(s)

class PolicyLifeCylerator(private val incomingPolicyEvents: MsgConsumer<String, String>,
                          private val outgoingBankEvents: MsgProducer<String, String>,
                          private val policyStore: PolicyStore,
                          private val theClock: TheClock) {

    fun processPolicies() {
        while (true) {
            val events = incomingPolicyEvents.poll()
            for (event in events) {
                val payouts = parseIncomingJSON(event.key, event.value)
                payouts.forEach {
                    outgoingBankEvents.send(it.payoutType.toString(), Klaxon().toJsonString(it))
                }
            }
        }
    }



    fun createPolicy(application: Application, policyNumber: String? = null): Policy {
        if (application.applicationState != ApplicationState.OK) {
            throw UnapprovedApplicationException("Could not create policy for application ${application.id}")
        }
        val _policyNumber: String = policyNumber ?: policyStore.generatePolicyNumber()
        val clock: Clock = theClock.clock
        val now = ZonedDateTime.now(clock)
        return Policy(application, now, _policyNumber)
    }

    private fun parseIncomingJSON(key: String, value: String): List<Payout> {
       return when (key) {
            "death" -> {
                val death = Klaxon().parse<Death>(value)
                if (death != null) {
                    val insured = policyStore.getInsured(death.insuredTIN)
                    insured?.initPolicies(policyStore)
                    insured?.died(theClock.dateOrNow(death.dateDeceased)) ?: listOf()

                } else listOf<Payout>()
            }
            "premium" -> {
                val payment = Klaxon().parse<Payment>(value)
                if (payment != null) {
                    val policy = policyStore.getPolicy(payment.policyNumber)
                    policy?.gotDeposit(theClock.dateOrNow(payment.date))
                }
                listOf()
            }
            "cancel" -> {
                val cancellation = Klaxon().parse<Cancellation>(value)
                val payout = if (cancellation != null) {
                    val policy = policyStore.getPolicy(cancellation.policyNumber)
                    policy?.cancel(theClock.dateOrNow(cancellation.date))
                } else null
                payout?.let { listOf(it) } ?: listOf()
            }
            "tick" -> {
                val update = Klaxon().parse<PolicyUpdate>(value)
                val payout = if (update != null) {
                    val policy = policyStore.getPolicy(update.policyNumber)
                    policy?.clockTick(theClock.dateOrNow(update.date))
                } else null
                payout?.let { listOf(it) } ?: listOf()
            }
                else -> listOf()
        }
    }
}
