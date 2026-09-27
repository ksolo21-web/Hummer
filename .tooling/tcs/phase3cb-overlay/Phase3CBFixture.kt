package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import java.io.File

internal class Phase3CBFixture(phone:Boolean=false,inventory:Boolean=true):AutoCloseable {
    val f=Phase2DBFixture(phone)
    val building=BuildingGeometry("building-1","1","apartment",true,"",listOf("1"),
        listOf(BuildingLabelItem("1",Point2D(350.0,165.0),null,0.0,10.0)),
        listOf(Point2D(300.0,140.0),Point2D(400.0,140.0),Point2D(400.0,190.0),Point2D(300.0,190.0)))
    val slot=f.slot.copy(roads=f.input.assignment.roads,roadCount=3,namedRoadCount=3,roadNameInventory=f.input.assignment.roads.map {it.name},buildings=listOf(building),buildingCount=1)
    val kb=f.kb.copy(assignments=f.kb.assignments+(f.identity.displayId to slot))
    val id=f.identity.displayId;val mode=f.mode
    val root=File(f.root,"extended-drafts")
    val store=AndroidExtendedDraftStore(root,kb,f.sources,f.coordinator)
    init {if(inventory)f.prepare()}
    fun catalog()=store.catalog(id,mode)
    fun draft()=store.read(id,mode)!!
    fun create()=store.create(id,mode,catalog().binding)
    fun proposal(kind:ExtendedKind,itemId:String?=null):ExtendedProposal {
        val item=catalog().items.first {it.kind==kind && (itemId==null || it.id==itemId)}
        val v=when(val b=item.before) {
            is PathValue -> b.copy(points=b.points.mapIndexed {i,p->if(i==0)p.copy(x=p.x+2)else p})
            is WorkValue -> WorkValue("yellow","perimeter","right",false)
            is BuildingValue -> b.copy(label="2",members=listOf("2"),labels=b.labels.map {it.copy(text="2")})
            is AddressValue -> b.copy(street="102 Verified Example Way")
            is PhoneValue -> if(b.state==ProposedPhoneState.UNAVAILABLE)PhoneValue(ProposedPhoneState.UNKNOWN,"") else PhoneValue(ProposedPhoneState.UNAVAILABLE,"")
        }
        val e=item.evidence.first();return ExtendedProposal(kind,item.id,item.before,v,"Checked reference; independent reconciliation still required",e.role,e.sha256)
    }
    fun seed(kinds:List<ExtendedKind> = catalog().items.map {it.kind}.distinct()) {val d=create();store.save(id,mode,d.latest.token,kinds.map {proposal(it)})}
    override fun close()=f.close()
}
