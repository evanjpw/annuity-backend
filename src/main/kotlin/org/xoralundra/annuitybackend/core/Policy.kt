package org.xoralundra.annuitybackend.core

import com.beust.klaxon.*
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.joda.money.MoneyUtils
import org.xoralundra.annuitybackend.common.ApplicationState
import org.xoralundra.annuitybackend.common.PayoutType
import org.xoralundra.annuitybackend.common.PolicyState
import org.xoralundra.annuitybackend.mockapis.PolicyNumber
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount



class AnnuityInfo(
    val premium: Money,
    val payout: Money,
    val interestRate: Double,
    val cancellationFee: Money,
    @Json(ignored = false)
    private val termLength: TemporalAmount
    ) {
    fun termCompleteDate(startDate: ZonedDateTime): ZonedDateTime = startDate.plus(termLength)
    val monthlyInterest: Double = interestRate / 12.0
}

class Application(val insured: Insured, val annuityInfo: AnnuityInfo, val id: String) {
    private var hasBankAccount: Boolean? = null
    private var hasFunds: Boolean? = null
    private var isSolidCitizen: Boolean? = null

    val applicationState: ApplicationState
        get() {

            val approvals = listOf(hasBankAccount, hasFunds, isSolidCitizen)
            return when {
                approvals.any { it == false } -> ApplicationState.DISAPPROVE
                approvals.any { it == null } -> ApplicationState.WAIT
                else -> ApplicationState.OK
            }
        }
}

data class Payout(val sum: Money, val payoutType: PayoutType)

data class Payment(val sum: Money, val date: ZonedDateTime, val policyNumber: PolicyNumber)

class InvalidPolicyTransition(s: String? = null) : IllegalStateException(s)

data class Cancellation(val policyNumber: PolicyNumber, val date: ZonedDateTime)

data class PolicyUpdate(val policyNumber: PolicyNumber, val date: ZonedDateTime)

class Policy(
    val insured: Insured,
    val creationDate: ZonedDateTime,
    val annuityInfo: AnnuityInfo,
    val policyNumber: PolicyNumber,
    private var state: PolicyState = PolicyState.NEW,
    private var startDate: ZonedDateTime? = null,
    private var balance: Money = Money.zero(CurrencyUnit.USD),
    private var mostRecentMonthiversary: ZonedDateTime? = null
) {
    constructor(application: Application, creationDate: ZonedDateTime, policyNumber: PolicyNumber) : this(
        application.insured,
        creationDate,
        application.annuityInfo,
        policyNumber
        // Policy state and start date will always be def values
    )


    private fun transitionTo(newState: PolicyState) {
        if (!state.isTransitionValid(newState)) {
            throw InvalidPolicyTransition(
                "For policy $policyNumber, can't transition from $state to $newState"
            )
        }
    }

    private val nextMonthiversary get() = mostRecentMonthiversary?.plusMonths(1L)

    private fun recalculateBalance(dateAsOf: ZonedDateTime, fullMonth: Boolean = true): Unit {
        // For the time being, we will compound monthly. If there is time
        // we will try compounding daily  *
        if (fullMonth) {  // If we are compounding monthly do not pay interest on fractional months
            balance += balance.multipliedBy(annuityInfo.monthlyInterest, RoundingMode.HALF_UP)
        }
    }

    fun gotDeposit(dateAsOf: ZonedDateTime) {
        startDate = dateAsOf
        if (mostRecentMonthiversary == null) {
            mostRecentMonthiversary = startDate
        }
        transitionTo(PolicyState.CANCELLABLE)
    }

    fun cancel(dateAsOf: ZonedDateTime): Payout {
        recalculateBalance(dateAsOf)
        val oldState = state
        transitionTo(PolicyState.CANCELLED)
        if (oldState != PolicyState.CANCELLABLE) {
            balance -= annuityInfo.cancellationFee
        }
        return Payout(balance, PayoutType.REFUND)
    }

    fun ownerDied(dateAsOf: ZonedDateTime): Payout {
        val targetDate = nextMonthiversary
        recalculateBalance(dateAsOf, targetDate != null && dateAsOf.isAfter(targetDate))
        transitionTo(PolicyState.RIP)
        return Payout(balance, PayoutType.TO_BENEFICIARIES)
    }

    fun clockTick(dateAsOf: ZonedDateTime): Payout? {
        val targetDate = nextMonthiversary
        if (targetDate != null && dateAsOf.isAfter(targetDate)) {
            recalculateBalance(targetDate)
            if (state == PolicyState.CANCELLABLE) {
                transitionTo(PolicyState.ACTIVE)
            }
            if (startDate != null && targetDate.isAfter(annuityInfo.termCompleteDate(startDate!!))) {
                transitionTo(PolicyState.COMPLETED)
                return Payout(balance, PayoutType.TERM_COMPLETE)
            }
            val payout = MoneyUtils.min(balance, annuityInfo.payout)
            balance -= payout
            mostRecentMonthiversary = targetDate
            return Payout(payout, PayoutType.PAYMENT)
        }
        return null
    }
}
