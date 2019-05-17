package org.xoralundra.annuitybackend.core

import org.xoralundra.annuitybackend.common.PolicyState
import org.joda.money.*
import java.util.*

class Application(val insured: Insured, val premium: Money, val payout: Money, val interestRate: Double) {
    private var hasBankAccount: Boolean? = null
    private var hasFunds: Boolean? = null
    private var isSolidCitizen: Boolean? = null


}

class Policy (val insured: Insured, val creationDate: Date) {
    private var state = PolicyState.NEW
    private var startDate: Date? = null
}//
