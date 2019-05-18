package org.xoralundra.annuitybackend.core

import com.beust.klaxon.Klaxon
import org.xoralundra.annuitybackend.common.ApplicationState
import org.xoralundra.annuitybackend.mockapis.*
import java.time.Clock
import java.time.ZonedDateTime

fun main() {
}

class UnapprovedApplicationException(s: String? = null) : Exception(s)

class PolicyLifeCylerator(private val incomingPolicyEvents: MsgConsumer<String, String>,
                          private val outgoingBankEvents: MsgProducer<String, String>,
                          private val policyStore: PolicyStore,
                          private val theClock: TheClock) {



    fun createPolicy(application: Application, policyNumber: String? = null): Policy {
        if (application.applicationState != ApplicationState.OK) {
            throw UnapprovedApplicationException("Could not create policy for application ${application.id}")
        }
        val _policyNumber: String = policyNumber ?: policyStore.generatePolicyNumber()
        val clock: Clock = theClock.clock
        val now = ZonedDateTime.now(clock)
        return Policy(application, now, _policyNumber)
    }

    fun parseIncomingJSON(key: String, value: String) {
        when (key) {
            "death" -> {
                val death = Klaxon().parse<Death>(value)
                if (death != null) {
                    val insured = policyStore.getInsured(death.insuredTIN)
                    insured?.initPolicies(policyStore)
                    insured?.died(theClock.dateOrNow(death.dateDeceased))
                }
            }
            "premium" -> {
                val payment = Klaxon().parse<Payment>(value)
                if (payment != null) {
                    val policy = policyStore.getPolicy(payment.policyNumber)
                    policy?.gotDeposit(theClock.dateOrNow(payment.date))
                }
            }
            "cancel" -> {
                val cancellation = Klaxon().parse<Cancellation>(value)
                if (cancellation != null) {
                    val policy = policyStore.getPolicy(cancellation.policyNumber)
                    policy?.cancel(theClock.dateOrNow(cancellation.date))
                }
            }
            "tick" -> {
                val update = Klaxon().parse<PolicyUpdate>(value)
                if (update != null) {
                    val policy = policyStore.getPolicy(update.policyNumber)
                    policy?.clockTick(theClock.dateOrNow(update.date))
                }
            }
        }
    }
}
