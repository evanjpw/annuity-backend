package org.xoralundra.annuitybackend.core

import mu.KotlinLogging
import org.xoralundra.annuitybackend.mockapis.PolicyNumber
import org.xoralundra.annuitybackend.mockapis.PolicyStore
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.ArrayList

class WrongInsuredException(s: String?) : IllegalArgumentException(s) {
    constructor() : this(null)
}

data class Address(
    val street1: String,
    val street2: String?,
    val city: String,
    val state: String?,
    val postalCode: String?,
    val countryCode: String
)

typealias TIN = String

data class Death(val date: java.time.ZonedDateTime, val insuredTIN: TIN)

class Insured(
    val name: String,
    val address: Address,
    val phoneNumber: String,
    val dateOfBirth: Date,
    val taxpayerIdentificationNumber: String,
    var policyNumbers: Array<PolicyNumber>
) {
    val logger = KotlinLogging.logger {  }
    private var _dateOfDeath: ZonedDateTime? = null
    val isAlive get() = _dateOfDeath == null
    val dateOfDeath get() = _dateOfDeath

    fun died(deathDate: ZonedDateTime? = null): List<Payout> {
        _dateOfDeath = deathDate ?: ZonedDateTime.now()
        logger.info { "In insured $taxpayerIdentificationNumber died. There are ${policies.size} policies" }
        return _policies.map { policy ->
            logger.info {
                "In insured died processing policy ${policy.policyNumber}"
            }
            policy.ownerDied(_dateOfDeath!!)
        }
    }

    private val _policies = ArrayList<Policy>()

    fun initPolicies(policyStore: PolicyStore) {
        for (policyNumber in policyNumbers) {
            val policy = policyStore.getPolicy(policyNumber)
            if (policy != null && policy !in _policies) {
                _policies.add(policy) // yes this skips a verification
            }
        }
    }

    val policies: List<Policy> get() = _policies.toList()

    fun addPolicy(policy: Policy) {
        if (policy.insured != this) {
            throw WrongInsuredException()
        }
        if (policy.policyNumber !in policyNumbers) {
            policyNumbers =  policyNumbers.plusElement(policy.policyNumber)
            _policies.add(policy)
        }
    }
}
