package org.xoralundra.annuitybackend.core

import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import mu.KotlinLogging
import org.joda.money.Money
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

    private val logger = KotlinLogging.logger {  }

    fun processPolicies(exit: Boolean = false) {
        do {
            try {
                val events = incomingPolicyEvents.poll()
                for (event in events) {
                    val payouts = parseIncomingJSON(event.key, event.value)
                    payouts.forEach {
                        outgoingBankEvents.send(it.payoutType.toString(), Klaxon().toJsonString(it))
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Exception processing policies" }
                if (exit) {
                    throw e
                }
            }
        } while (!exit)
    }



    fun createPolicy(application: Application, policyNumber: String? = null): Policy {
        if (application.applicationState != ApplicationState.OK) {
            throw UnapprovedApplicationException("Could not create policy for application ${application.id}")
        }
        val _policyNumber: String = policyNumber ?: policyStore.generatePolicyNumber()
        val clock: Clock = theClock.clock
        val now = ZonedDateTime.now(clock)
        val policy = Policy(application, now, _policyNumber)
        policy.insured.addPolicy(policy)
        policyStore.savePolicy(policy)
        policyStore.saveInsured(policy.insured)
        return policy
    }

    private fun parseIncomingJSON(key: String, value: String): List<Payout> {
        logger.info { "In parse incoming JSON. Keys is $key, value is $value." }
       return when (key) {
            "death" -> {
                val death = Klaxon().converter(dateConverter).parse<Death>(value)
                if (death != null) {
                    val insured = policyStore.getInsured(death.insuredTIN)
                    insured?.initPolicies(policyStore)
                    val payouts = insured?.died(theClock.dateOrNow(death.date)) ?: listOf()
                    policyStore.saveInsured(insured)
                    insured?.policies?.forEach {
                        policyStore.savePolicy(it)
                    }
                    payouts
                } else listOf<Payout>()
            }
            "premium" -> {
                val payment = Klaxon().converter(dateConverter).converter(sumConverter).parse<Payment>(value)
                if (payment != null) {
                    val policy = policyStore.getPolicy(payment.policyNumber)
                    logger.info {
                        "Premium received. Policy is $policy (${payment.policyNumber}), payment date is ${payment.date}"
                    }
                    policy?.gotDeposit(theClock.dateOrNow(payment.date))
                    logger.info { "Policy state is ${policy?.policyState}" }
                    policyStore.savePolicy(policy)
                }
                listOf()
            }
            "cancel" -> {
                val cancellation = Klaxon().converter(dateConverter).parse<Cancellation>(value)
                val payout = if (cancellation != null) {
                    val policy = policyStore.getPolicy(cancellation.policyNumber)
                    val possiblePayout = policy?.cancel(theClock.dateOrNow(cancellation.date))
                    policyStore.savePolicy(policy)
                    possiblePayout
                } else null
                payout?.let { listOf(it) } ?: listOf()
            }
            "tick" -> {
                val update = Klaxon().converter(dateConverter).parse<PolicyUpdate>(value)
                val payout = if (update != null) {
                    val policy = policyStore.getPolicy(update.policyNumber)
                    val possiblePayout = policy?.clockTick(theClock.dateOrNow(update.date))
                    policyStore.savePolicy(policy)
                    possiblePayout
                } else null
                payout?.let { listOf(it) } ?: listOf()
            }
                else -> listOf()
        }
    }
}

val sumConverter = object: Converter {
    override fun canConvert(cls: Class<*>) = cls == Money::class.java

    override fun fromJson(jv: JsonValue) =
            if (jv.string != null) {
                Money.parse(jv.string)
            } else {
                throw KlaxonException("Could not parse sum: ${jv.string}")
            }

    override fun toJson(o: Any) = """ { "sum": $o }"""
}

val dateConverter = object: Converter {
    override fun canConvert(cls: Class<*>)
            = cls == ZonedDateTime::class.java

    override fun fromJson(jv: JsonValue) =
        if (jv.string != null) {
            //, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            ZonedDateTime.parse(jv.string)
        } else {
            throw KlaxonException("Couldn't parse date: ${jv.string}")
        }

    override fun toJson(o: Any)
            = """ { "date" : $o } """
}
