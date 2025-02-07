package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

data class HasExplain(val explainType: ExplainPlanType) : Component {
    enum class ExplainPlanType {
        NONE,
        SAFE,
        FULL
    }
}
