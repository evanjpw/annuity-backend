package org.xoralundra.annuitybackend.mockapis

import org.xoralundra.annuitybackend.core.Insured
import org.xoralundra.annuitybackend.core.Policy
import org.xoralundra.annuitybackend.core.TIN
import java.util.*

typealias PolicyNumber = String

open class PolicyStore {
    private val policies = HashMap<PolicyNumber, Policy>()

    private val insureds = HashMap<TIN, Insured>()

    // This does not create a real policy ID
    fun generatePolicyNumber(): PolicyNumber = UUID.randomUUID().toString()

    fun getInsured(tin: TIN): Insured? = insureds[tin]

    fun saveInsured(insured: Insured) {
        insureds[insured.taxpayerIdentificationNumber] = insured
    }

    fun getPolicy(policyNumber: PolicyNumber): Policy? = policies[policyNumber]

    fun savePolicy(policy: Policy) {
        policies[policy.policyNumber] = policy
    }
}

object ThePolicyStore : PolicyStore()