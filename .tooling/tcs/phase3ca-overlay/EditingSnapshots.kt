package com.koenterprises.territorycardstudio

import java.util.Collections

/** Detached core data-transfer graph, including fields outside canonical hash projections. */
internal object EditingSnapshots {
    @Suppress("UNCHECKED_CAST")
    fun <T> detach(value:T):T {
        var nodes=0
        fun copy(v:Any?,depth:Int):Any? {
            require(++nodes<=100000 && depth<=64) { "Editing input graph exceeds bounds" }
            return when(v) {
                null,is String,is Boolean,is Int,is Long,is Double,is Float,is Short,is Byte,is Char,is Enum<*> -> v
                is Page2Inventory.LetterWriting -> Page2Inventory.LetterWriting(copy(v.inventory,depth+1) as com.koenterprises.territorycardstudio.core.LetterWritingAddressInventory)
                is Page2Inventory.Telephone -> Page2Inventory.Telephone(copy(v.inventory,depth+1) as com.koenterprises.territorycardstudio.core.TelephoneTerritoryInventory)
                is List<*> -> Collections.unmodifiableList(v.map {copy(it,depth+1)})
                is Set<*> -> Collections.unmodifiableSet(v.mapTo(linkedSetOf()){copy(it,depth+1)})
                is Map<*,*> -> Collections.unmodifiableMap(v.entries.associate {copy(it.key,depth+1) to copy(it.value,depth+1)})
                else -> {
                    require(v.javaClass.name.startsWith("com.koenterprises.territorycardstudio.core.")) { "Unsupported editing value" }
                    val components=v.javaClass.methods.filter {it.name.matches(Regex("component[0-9]+")) && it.parameterCount==0}
                        .sortedBy {it.name.removePrefix("component").toInt()}
                    val method=v.javaClass.methods.singleOrNull {it.name=="copy" && it.parameterCount==components.size}
                    require(components.isNotEmpty() && method!=null) { "Editing value is not a supported data record: ${v.javaClass.simpleName}" }
                    method.invoke(v,*components.map {copy(it.invoke(v),depth+1)}.toTypedArray())
                }
            }
        }
        return copy(value,0) as T
    }
}
