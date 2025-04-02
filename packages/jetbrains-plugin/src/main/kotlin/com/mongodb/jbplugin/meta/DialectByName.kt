package com.mongodb.jbplugin.meta

import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.dialects.springquery.SpringAtQueryDialect
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasSourceDialect.DialectName.JAVA_DRIVER
import com.mongodb.jbplugin.mql.components.HasSourceDialect.DialectName.SPRING_CRITERIA
import com.mongodb.jbplugin.mql.components.HasSourceDialect.DialectName.SPRING_QUERY
import com.mongodb.jbplugin.mql.components.HasSourceDialect.DialectName.UNKNOWN

fun dialectByName(name: HasSourceDialect.DialectName): Dialect<*, *> {
    return when (name) {
        JAVA_DRIVER -> JavaDriverDialect
        SPRING_CRITERIA -> SpringCriteriaDialect
        SPRING_QUERY -> SpringAtQueryDialect
        UNKNOWN -> JavaDriverDialect
    }
}
