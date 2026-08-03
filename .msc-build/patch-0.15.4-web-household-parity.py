from pathlib import Path
import re, sys

root=Path(sys.argv[1])
wb=root/'MyStudyCompanionWeb/workbook.js'
text=wb.read_text()

helpers=r'''
const PROFESSIONAL_WORKBOOK_BASE="assets/workbook";
let professionalWorkbookManifestPromise;
const professionalWorkbookAssetCache=new Map();

async function loadProfessionalWorkbookManifest(){
  professionalWorkbookManifestPromise ||= fetch(`${PROFESSIONAL_WORKBOOK_BASE}/manifest.json`,{cache:"no-cache"}).then(response=>{
    if(!response.ok) throw new Error(`Professional workbook manifest failed (${response.status})`);
    return response.json();
  }).then(manifest=>{
    if(Number(manifest.version)<3||!Array.isArray(manifest.assets)||manifest.assets.length<16) throw new Error("Professional workbook pack is incomplete.");
    return manifest;
  });
  return professionalWorkbookManifestPromise;
}
function professionalAssetIdFor(activity){
  const source=`${activity?.id||""} ${activity?.title||""} ${activity?.instructions||""}`.toLowerCase();
  const slugs=["creation","noahs-ark","jonah","david-goliath","daniel-lions","jesus-storm","good-samaritan","lost-sheep","talents","prodigal-son","wise-builders","armor-of-god","favorite-scripture","favorite-animal","faith-action","gratitude-journal"];
  const direct=slugs.find(slug=>source.includes(slug));if(direct)return direct;
  if(["noah","ark","flood"].some(x=>source.includes(x)))return"noahs-ark";
  if(["jonah","whale","great fish"].some(x=>source.includes(x)))return"jonah";
  if(["david","goliath"].some(x=>source.includes(x)))return"david-goliath";
  if(["daniel","lion"].some(x=>source.includes(x)))return"daniel-lions";
  if(["storm","boat","sea"].some(x=>source.includes(x)))return"jesus-storm";
  if(["samaritan","neighbor","compassion"].some(x=>source.includes(x)))return"good-samaritan";
  if(["sheep","found","lost"].some(x=>source.includes(x)))return"lost-sheep";
  if(["talent","harvest","seed","grow"].some(x=>source.includes(x)))return"talents";
  if(["prodigal","return","forgive"].some(x=>source.includes(x)))return"prodigal-son";
  if(["builder","rock","foundation"].some(x=>source.includes(x)))return"wise-builders";
  if(["armor","shield","courage","stand firm"].some(x=>source.includes(x)))return"armor-of-god";
  if(["animal","creation","garden","earth"].some(x=>source.includes(x)))return"favorite-animal";
  if(["family","gratitude","thank","bible study"].some(x=>source.includes(x)))return"gratitude-journal";
  if(["faith","action","choice","decision"].some(x=>source.includes(x)))return"faith-action";
  if(["scripture","lamp","path","light"].some(x=>source.includes(x)))return"favorite-scripture";
  return {CREATION_GARDEN:"creation",COURAGE_SHIELD:"armor-of-god",SEED_AND_GROWTH:"talents",FAMILY_BIBLE:"gratitude-journal",LAMP_AND_PATH:"favorite-scripture"}[activity?.artTemplate]||"favorite-scripture";
}
function loadWorkbookImage(url){return new Promise((resolve,reject)=>{const image=new Image();image.decoding="async";image.onload=()=>resolve(image);image.onerror=()=>reject(new Error(`Could not load ${url}`));image.src=url;});}
async function loadProfessionalWorkbookAsset(activity){
  const manifest=await loadProfessionalWorkbookManifest(),id=professionalAssetIdFor(activity),key=`${manifest.version}:${id}`;
  if(professionalWorkbookAssetCache.has(key))return professionalWorkbookAssetCache.get(key);
  const promise=(async()=>{
    const meta=manifest.assets.find(asset=>asset.id===id)||manifest.assets[Math.abs(String(activity?.id||id).split("").reduce((a,c)=>((a*31)+c.charCodeAt(0))|0,0))%manifest.assets.length];
    const base=`${PROFESSIONAL_WORKBOOK_BASE}/${meta.id}`;
    const [master,line,changed,regionMask,step1,step2]=await Promise.all([
      loadWorkbookImage(`${base}/master.webp`),loadWorkbookImage(`${base}/line.webp`),loadWorkbookImage(`${base}/difference-changed.webp`),loadWorkbookImage(`${base}/region-mask.png`),loadWorkbookImage(`${base}/drawing-step-1.webp`),loadWorkbookImage(`${base}/drawing-step-2.webp`)
    ]);
    const maskCanvas=document.createElement("canvas");maskCanvas.width=manifest.width;maskCanvas.height=manifest.height;
    const maskContext=maskCanvas.getContext("2d",{willReadFrequently:true});maskContext.drawImage(regionMask,0,0,manifest.width,manifest.height);
    const regionMaskData=maskContext.getImageData(0,0,manifest.width,manifest.height);
    return {manifest,meta,master,line,changed,regionMask,regionMaskData,steps:[step1,step2,line,master]};
  })();
  professionalWorkbookAssetCache.set(key,promise);return promise;
}
function workbookPaletteColor(manifest,number){return manifest.palette.find(entry=>Number(entry.number)===Number(number))?.rgb||"#252525";}
function drawImageCover(ctx,image,width,height){ctx.clearRect(0,0,width,height);ctx.fillStyle="#fff";ctx.fillRect(0,0,width,height);ctx.drawImage(image,0,0,width,height);}
function selectedRegionCss(progress,activityId,regionId){const value=progress.selectedColors[regionKey(activityId,String(regionId))];return value===undefined?null:argbToCss(value);}
function colorOverlayImageData(illustration,activityId,progress){
  const {manifest,regionMaskData}=illustration,out=new ImageData(manifest.width,manifest.height),src=regionMaskData.data,dst=out.data;
  for(let i=0;i<src.length;i+=4){const regionId=src[i];if(!regionId)continue;const css=selectedRegionCss(progress,activityId,regionId);if(!css||css==="#ffffff")continue;const rgb=parseInt(css.slice(1),16);dst[i]=(rgb>>16)&255;dst[i+1]=(rgb>>8)&255;dst[i+2]=rgb&255;dst[i+3]=205;}
  return out;
}
function drawProfessionalColorCanvas(ctx,illustration,activity,progress,showNumbers=true){
  const {manifest,meta,line}=illustration;ctx.clearRect(0,0,manifest.width,manifest.height);ctx.fillStyle="#fff";ctx.fillRect(0,0,manifest.width,manifest.height);ctx.putImageData(colorOverlayImageData(illustration,activity.id,progress),0,0);ctx.globalCompositeOperation="multiply";ctx.drawImage(line,0,0,manifest.width,manifest.height);ctx.globalCompositeOperation="source-over";
  if(showNumbers){ctx.textAlign="center";ctx.textBaseline="middle";ctx.font="bold 15px system-ui";for(const region of meta.regions){if(selectedRegionCss(progress,activity.id,region.id))continue;ctx.fillStyle="rgba(255,255,255,.82)";ctx.beginPath();ctx.arc(region.centerX,region.centerY,10,0,Math.PI*2);ctx.fill();ctx.fillStyle="#19242e";ctx.fillText(String(region.number),region.centerX,region.centerY+1);}}
}
async function professionalPrintData(activity,progress,completed){
  const illustration=await loadProfessionalWorkbookAsset(activity),{manifest,meta}=illustration,canvas=document.createElement("canvas"),ctx=canvas.getContext("2d");canvas.width=manifest.width;canvas.height=manifest.height;
  if(activity.kind==="COLOR_BY_NUMBER")drawProfessionalColorCanvas(ctx,illustration,activity,completed?progress:{...progress,selectedColors:{}},!completed);
  else {const step=Math.max(0,Math.min(illustration.steps.length-1,progress.drawingSteps?.[activity.id]||0));drawImageCover(ctx,completed?illustration.steps[step]:illustration.line,manifest.width,manifest.height);if(completed)drawStoredStrokes(ctx,progress.strokes||[],manifest.width,manifest.height);}
  return {dataUrl:canvas.toDataURL("image/png"),aspectRatio:meta.aspectRatio,title:meta.title};
}
function drawStoredStrokes(ctx,strokes,width,height){ctx.lineCap="round";ctx.lineJoin="round";for(const stroke of strokes){const points=stroke.points||decode(stroke.encodedPoints);if(points.length<2)continue;ctx.beginPath();ctx.moveTo(points[0].x/1000*width,points[0].y/1000*height);points.slice(1).forEach(point=>ctx.lineTo(point.x/1000*width,point.y/1000*height));ctx.globalAlpha=stroke.tool==="HIGHLIGHTER"?.38:1;ctx.strokeStyle=stroke.tool==="ERASER"?"#fff":strokeCss(stroke);ctx.lineWidth=Math.max(1,Number(stroke.width)||4)*width/1000;ctx.stroke();}ctx.globalAlpha=1;}
'''
needle='const ART_TEMPLATES=["LAMP_AND_PATH","CREATION_GARDEN","COURAGE_SHIELD","SEED_AND_GROWTH","FAMILY_BIBLE"];\n'
assert needle in text
text=text.replace(needle,needle+helpers+'\n',1)

text=text.replace('else if(a.kind==="DRAWING"){const steps=drawingSteps(a.artTemplate),index=Math.max(0,Math.min(steps.length-1,progress.drawingSteps[a.id]||0)),step=document.createElement("div");step.className="wb-card";step.innerHTML=`<p class="eyebrow">Step ${index+1} of ${steps.length}</p><p><strong>${esc(steps[index])}</strong></p><div class="wb-row"><button class="prev" ${index===0?"disabled":""}>Previous</button><button class="next" ${index===steps.length-1?"disabled":""}>Next</button></div>`;step.querySelector(".prev").onclick=()=>{progress.drawingSteps[a.id]=Math.max(0,index-1);persist();render();};step.querySelector(".next").onclick=()=>{progress.drawingSteps[a.id]=Math.min(steps.length-1,index+1);persist();render();};card.append(step);addTextarea(card,progress,`${a.id}:caption`,"Explain my drawing and the lesson",3);}', 'else if(a.kind==="DRAWING")renderProfessionalDrawingSteps(card,a,progress);',1)
text=text.replace('if(inkSection)setupInk(page,progress,artActivity?.artTemplate||null,artActivity?.id||`${page.key}:free-ink`);','if(inkSection)setupInk(page,progress,artActivity||null,artActivity?.id||`${page.key}:free-ink`);',1)

start=text.index('  function renderDifferences(card,a,progress){')
end=text.index('\n  function renderMatching',start)
new_diff=r'''  async function renderDifferences(card,a,progress){
    const loading=document.createElement("p");loading.className="muted";loading.textContent="Loading the professional comparison scene…";card.append(loading);
    try{
      const illustration=await loadProfessionalWorkbookAsset(a),spots=illustration.meta.differences,found=new Set(progress.foundPuzzleIds);loading.remove();
      const status=document.createElement("p");status.className="muted";status.textContent=`${spots.filter(spot=>found.has(`${a.id}:${spot.id}`)).length} of ${spots.length} differences found. Tap a real change in the second professional illustration.`;card.append(status);
      const wrap=document.createElement("div");wrap.className="wb-difference-grid";
      [["Original",illustration.master,false],["Changed picture",illustration.changed,true]].forEach(([label,image,changed])=>{const panel=document.createElement("div");panel.innerHTML=`<strong>${label}</strong>`;const canvas=document.createElement("canvas");canvas.width=illustration.manifest.width;canvas.height=illustration.manifest.height;canvas.className="wb-art-canvas";const ctx=canvas.getContext("2d");drawImageCover(ctx,image,canvas.width,canvas.height);if(changed){const drawFound=()=>{for(const spot of spots.filter(s=>found.has(`${a.id}:${s.id}`))){ctx.beginPath();ctx.arc(spot.x/1000*canvas.width,spot.y/1000*canvas.height,spot.radius/1000*Math.min(canvas.width,canvas.height)*.72,0,Math.PI*2);ctx.strokeStyle="#2e7d32";ctx.lineWidth=4;ctx.stroke();}};drawFound();canvas.onclick=e=>{const rect=canvas.getBoundingClientRect(),x=(e.clientX-rect.left)/rect.width*1000,y=(e.clientY-rect.top)/rect.height*1000,spot=spots.find(s=>Math.hypot(x-s.x,y-s.y)<=s.radius);if(!spot)return;found.add(`${a.id}:${spot.id}`);progress.foundPuzzleIds=[...found];persist();render();};}panel.append(canvas);wrap.append(panel);});card.append(wrap);
      const reset=document.createElement("button");reset.textContent="Reset differences";reset.onclick=()=>{progress.foundPuzzleIds=progress.foundPuzzleIds.filter(id=>!id.startsWith(`${a.id}:`));persist();render();};card.append(reset);
    }catch(error){loading.textContent=`The professional illustration could not be loaded: ${error.message}`;loading.classList.add("error");}
  }
'''
text=text[:start]+new_diff+text[end:]

start=text.index('  function renderColorByNumber(card,a,progress){')
end=text.index('\n  function setupInk',start)
new_color=r'''  async function renderColorByNumber(card,a,progress){
    const loading=document.createElement("p");loading.className="muted";loading.textContent="Loading the professional numbered illustration…";card.append(loading);
    try{
      const illustration=await loadProfessionalWorkbookAsset(a),{manifest,meta}=illustration;loading.remove();
      let selected=Number(progress.selectedColorNumbers[a.id]||1);const legend=document.createElement("div");legend.className="wb-tools";
      for(const entry of manifest.palette){const button=document.createElement("button");button.classList.toggle("selected",selected===entry.number);button.innerHTML=`<span style="display:inline-block;width:1.1rem;height:1.1rem;border-radius:50%;background:${entry.rgb};vertical-align:middle;border:1px solid #333"></span> ${entry.number} • ${esc(entry.label)}`;button.onclick=()=>{progress.selectedColorNumbers[a.id]=entry.number;persist();render();};legend.append(button);}card.append(legend);
      const feedback=document.createElement("p");feedback.className="muted";feedback.textContent=`Color ${selected} selected`;card.append(feedback);
      const canvas=document.createElement("canvas");canvas.width=manifest.width;canvas.height=manifest.height;canvas.className="wb-art-canvas";const ctx=canvas.getContext("2d");drawProfessionalColorCanvas(ctx,illustration,a,progress,true);
      canvas.onclick=e=>{const rect=canvas.getBoundingClientRect(),x=Math.max(0,Math.min(manifest.width-1,Math.floor((e.clientX-rect.left)/rect.width*manifest.width))),y=Math.max(0,Math.min(manifest.height-1,Math.floor((e.clientY-rect.top)/rect.height*manifest.height))),regionId=illustration.regionMaskData.data[(y*manifest.width+x)*4];if(!regionId)return;const region=meta.regions.find(item=>Number(item.id)===regionId);if(!region)return;if(Number(region.number)!==selected){feedback.textContent=`That region is number ${region.number}. Select color ${region.number}.`;return;}const key=regionKey(a.id,region.id),after=workbookPaletteColor(manifest,region.number),before=selectedRegionCss(progress,a.id,region.id);if(before===after){feedback.textContent="That region is already complete.";return;}progress.colorUndo.push({activityId:a.id,regionKey:key,beforeColor:before,afterColor:after});progress.colorUndo=progress.colorUndo.slice(-120);progress.colorRedo=[];progress.selectedColors[key]=cssToArgb(after);feedback.textContent=`Correct — region ${region.id} saved`;persist();render();};card.append(canvas);
      const complete=meta.regions.filter(region=>selectedRegionCss(progress,a.id,region.id)===workbookPaletteColor(manifest,region.number)).length,footer=document.createElement("div");footer.innerHTML=`<p><strong>${complete===meta.regions.length?"Picture complete — every professional region is correct and saved.":`${complete} of ${meta.regions.length} regions complete`}</strong></p><div class="wb-row"><button class="undo" ${progress.colorUndo.some(change=>change.activityId===a.id)?"":"disabled"}>Undo fill</button><button class="redo" ${progress.colorRedo.some(change=>change.activityId===a.id)?"":"disabled"}>Redo fill</button><button class="reset">Reset picture</button></div>`;
      footer.querySelector(".undo").onclick=()=>{const i=progress.colorUndo.findLastIndex(change=>change.activityId===a.id);if(i<0)return;const change=progress.colorUndo.splice(i,1)[0];if(change.beforeColor)progress.selectedColors[change.regionKey]=cssToArgb(change.beforeColor);else delete progress.selectedColors[change.regionKey];progress.colorRedo.push(change);persist();render();};footer.querySelector(".redo").onclick=()=>{const i=progress.colorRedo.findLastIndex(change=>change.activityId===a.id);if(i<0)return;const change=progress.colorRedo.splice(i,1)[0];progress.selectedColors[change.regionKey]=cssToArgb(change.afterColor);progress.colorUndo.push(change);persist();render();};footer.querySelector(".reset").onclick=()=>{meta.regions.forEach(region=>delete progress.selectedColors[regionKey(a.id,region.id)]);progress.colorUndo=progress.colorUndo.filter(change=>change.activityId!==a.id);progress.colorRedo=progress.colorRedo.filter(change=>change.activityId!==a.id);persist();render();};card.append(footer);
    }catch(error){loading.textContent=`The professional illustration could not be loaded: ${error.message}`;loading.classList.add("error");}
  }

  async function renderProfessionalDrawingSteps(card,a,progress){
    const shell=document.createElement("div");shell.className="wb-card";shell.innerHTML='<p class="muted">Loading the professional guided-drawing stages…</p>';card.append(shell);addTextarea(card,progress,`${a.id}:caption`,"Explain my drawing and the lesson",3);
    try{const illustration=await loadProfessionalWorkbookAsset(a),steps=illustration.meta.drawingSteps,index=Math.max(0,Math.min(steps.length-1,Number(progress.drawingSteps[a.id]||0)));shell.innerHTML=`<p class="eyebrow">Step ${index+1} of ${steps.length}</p><p><strong>${esc(steps[index])}</strong></p><img class="wb-professional-stage" src="${PROFESSIONAL_WORKBOOK_BASE}/${illustration.meta.id}/${index===0?"drawing-step-1.webp":index===1?"drawing-step-2.webp":index===2?"line.webp":"master.webp"}" alt="${esc(illustration.meta.title)} drawing step ${index+1}"><div class="wb-row"><button class="prev" ${index===0?"disabled":""}>Previous</button><button class="next" ${index===steps.length-1?"disabled":""}>Next</button></div>`;shell.querySelector(".prev").onclick=()=>{progress.drawingSteps[a.id]=Math.max(0,index-1);persist();render();};shell.querySelector(".next").onclick=()=>{progress.drawingSteps[a.id]=Math.min(steps.length-1,index+1);persist();render();};}catch(error){shell.innerHTML=`<p class="error">The guided-drawing stage could not be loaded: ${esc(error.message)}</p>`;}
  }
'''
text=text[:start]+new_color+text[end:]

start=text.index('  function setupInk(page,progress,template=null,activityId){')
end=text.index('\n  return {open,close,eventBook,familyBook,activityLibraryBook};',start)
new_ink=r'''  async function setupInk(page,progress,activity=null,activityId){
    const tools=document.getElementById("inkTools");tool=progress.inkTools[activityId]||tool;color=progress.inkColors[activityId]||color;tools.innerHTML="";Object.keys(TOOLS).forEach(name=>{const button=document.createElement("button");button.textContent=name.toLowerCase();button.classList.toggle("selected",name===tool);button.onclick=()=>{tool=name;progress.inkTools[activityId]=name;persist();render();};tools.append(button)});PALETTE.forEach(candidate=>{const button=document.createElement("button");button.className="swatch";button.style.background=candidate;button.classList.toggle("selected",candidate===color);button.setAttribute("aria-label",`Choose ${candidate}`);button.onclick=()=>{color=candidate;progress.inkColors[activityId]=candidate;persist();render();};tools.append(button)});
    const canvas=document.getElementById("workbookCanvas"),ctx=canvas.getContext("2d");let background=null;if(activity){try{const illustration=await loadProfessionalWorkbookAsset(activity),step=Math.max(0,Math.min(illustration.steps.length-1,Number(progress.drawingSteps[activity.id]||0)));background=illustration.steps[step];canvas.width=illustration.manifest.width;canvas.height=illustration.manifest.height;}catch(error){console.error(error);}}
    const draw=()=>drawCanvas(ctx,progress.strokes,background,canvas.width,canvas.height);draw();const point=e=>{const rect=canvas.getBoundingClientRect();return{x:Math.round((e.clientX-rect.left)/rect.width*1000),y:Math.round((e.clientY-rect.top)/rect.height*1000)}};canvas.onpointerdown=e=>{drawing=true;current=[point(e)];canvas.setPointerCapture(e.pointerId);};canvas.onpointermove=e=>{if(!drawing)return;const p=point(e);if(current.length<220)current.push(p);drawCanvas(ctx,[...progress.strokes,{tool,color,width:TOOLS[tool].width,points:current}],background,canvas.width,canvas.height);};canvas.onpointerup=()=>{if(current.length>1){progress.strokes.push({id:crypto.randomUUID(),tool,colorArgb:cssToArgb(color),width:TOOLS[tool].width,encodedPoints:current.map(p=>`${p.x},${p.y}`).join(";")});progress.strokes=progress.strokes.slice(-120);progress.redoStrokes=[];persist();}drawing=false;current=[];draw();};document.getElementById("undoInk").onclick=()=>{const stroke=progress.strokes.pop();if(stroke)progress.redoStrokes.push(stroke);persist();render();};document.getElementById("redoInk").onclick=()=>{const stroke=progress.redoStrokes.pop();if(stroke)progress.strokes.push(stroke);persist();render();};document.getElementById("clearInk").onclick=()=>{if(confirm("Clear all drawing and handwriting on this page?")){progress.redoStrokes=[...progress.strokes];progress.strokes=[];persist();render();}};
  }
  function drawCanvas(ctx,strokes,background,width,height){ctx.clearRect(0,0,width,height);ctx.fillStyle="#fff";ctx.fillRect(0,0,width,height);if(background)ctx.drawImage(background,0,0,width,height);drawStoredStrokes(ctx,strokes,width,height);}
'''
text=text[:start]+new_ink+text[end:]

start=text.index('function printBook(book,workbookState,completed){')
end=text.index('\nfunction esc(',start)
new_print=r'''async function printBook(book,workbookState,completed){
  const w=open("","_blank");if(!w)return;const rendered=[];
  for(const page of book.pages){const progress=workbookState.pageProgress[page.key]||{},art=page.activities.find(activity=>activity.kind==="DRAWING"||activity.kind==="COLOR_BY_NUMBER"),printArt=art?await professionalPrintData(art,progress,completed):null;rendered.push(`<article class="page"><small>${esc(page.eyebrow)}</small><h1>${esc(page.title)}</h1><p>${esc(page.scriptureReferences||"")}</p>${page.activities.map(activity=>`<section><h2>${esc(activity.title)}</h2><p>${esc(activity.instructions)}</p>${activity.kind==="DRAWING"||activity.kind==="COLOR_BY_NUMBER"?"":(activity.prompts||activity.puzzleEntries?.map(item=>item.clue)||activity.matchPairs?.map(item=>item.left)||["Response"]).map((question,index)=>`<div class="line">□ ${esc(typeof question==='string'?question:question.clue||question.left||question)}</div>${completed&&progress.textAnswers?.[`${activity.id}:text:${index}`]?`<p class="answer">${esc(progress.textAnswers[`${activity.id}:text:${index}`])}</p>`:""}`).join("")}</section>`).join("")}${printArt?`<img class="professional-print-art" src="${printArt.dataUrl}" alt="${esc(printArt.title)}">`:`<div class="draw"><b>Drawing and handwriting</b>${completed?`<svg viewBox="0 0 100 60">${svgStrokes(progress.strokes)}</svg>`:""}</div>`}</article>`);}
  w.document.write(`<!doctype html><title>${esc(book.title)}</title><style>@page{size:letter;margin:.4in}body{font-family:Arial;color:#111}.page{min-height:9.6in;page-break-after:always}h1{font-size:22px}h2{font-size:14px;margin:9px 0 3px}.line{border-bottom:1px solid #bbb;padding:4px}.answer{margin:2px 18px;color:#444}.draw{height:180px;border:1px solid #999;border-radius:10px;padding:8px;margin-top:12px}.draw svg{width:100%;height:150px}.professional-print-art{display:block;max-width:100%;max-height:6.2in;object-fit:contain;margin:10px auto 0}</style>${rendered.join("")}<script>onload=()=>setTimeout(()=>print(),350)<\/script>`);w.document.close();
}
'''
text=text[:start]+new_print+text[end:]
wb.write_text(text)

css=root/'MyStudyCompanionWeb/styles.css'
c=css.read_text()
if '.wb-professional-stage' not in c:
    c+='\n.wb-professional-stage{display:block;width:min(100%,34rem);height:auto;aspect-ratio:2/3;object-fit:contain;margin:.75rem auto;border:1px solid color-mix(in srgb,var(--text) 20%,transparent);border-radius:1rem;background:#fff}\n'
css.write_text(c)

repo=root/'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt'
k=repo.read_text()
k=k.replace('        val code = normalizeHouseholdInvitationCode(invitationCode)\n', '        val invitationCandidates = householdInvitationLookupCandidates(invitationCode)\n',1)
old='''                val inviteRef = db.collection(INVITATIONS).document(code)\n                val invite = transaction.get(inviteRef)\n                val householdId = invite.getString(FIELD_HOUSEHOLD_ID).orEmpty()\n                val status = invite.getString(FIELD_STATUS).orEmpty()\n                val expires = invite.getLong(FIELD_EXPIRES_AT_EPOCH_SECONDS) ?: 0L\n                require(invite.exists() && householdId.isNotBlank()) { "That invitation code was not found." }'''
new='''                var inviteRef: com.google.firebase.firestore.DocumentReference? = null\n                var invite: com.google.firebase.firestore.DocumentSnapshot? = null\n                for (candidate in invitationCandidates) {\n                    val candidateRef = db.collection(INVITATIONS).document(candidate)\n                    val candidateSnapshot = transaction.get(candidateRef)\n                    if (candidateSnapshot.exists()) {\n                        inviteRef = candidateRef\n                        invite = candidateSnapshot\n                        break\n                    }\n                }\n                val resolvedInviteRef = requireNotNull(inviteRef) { "That invitation code was not found. Ask the organizer to create a fresh code and try again." }\n                val resolvedInvite = requireNotNull(invite) { "That invitation code was not found. Ask the organizer to create a fresh code and try again." }\n                val resolvedCode = resolvedInviteRef.id\n                val householdId = resolvedInvite.getString(FIELD_HOUSEHOLD_ID).orEmpty()\n                val status = resolvedInvite.getString(FIELD_STATUS).orEmpty()\n                val expires = resolvedInvite.getLong(FIELD_EXPIRES_AT_EPOCH_SECONDS) ?: 0L\n                require(householdId.isNotBlank()) { "That invitation is damaged. Ask the organizer to create a fresh code." }'''
assert old in k
k=k.replace(old,new,1)
k=k.replace('                    inviteCode = code,','                    inviteCode = resolvedCode,',1)
k=k.replace('                transaction.update(inviteRef, mapOf(','                transaction.update(resolvedInviteRef, mapOf(',1)
old_norm='''internal fun normalizeHouseholdInvitationCode(value: String): String {\n    val normalized = value.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "")\n    require(normalized.length in 6..32) { "Enter a valid household invitation code." }\n    return normalized\n}'''
new_norm='''internal fun normalizeHouseholdInvitationCode(value: String): String {\n    val compact = value.trim().uppercase().filter(Char::isLetterOrDigit)\n    require(compact.length in 6..32) { "Enter a valid household invitation code." }\n    return when (compact.length) {\n        8 -> compact.chunked(4).joinToString("-")\n        10 -> compact.chunked(5).joinToString("-")\n        else -> compact\n    }\n}\n\ninternal fun householdInvitationLookupCandidates(value: String): List<String> {\n    val compact = value.trim().uppercase().filter(Char::isLetterOrDigit)\n    val canonical = normalizeHouseholdInvitationCode(value)\n    return linkedSetOf(\n        canonical,\n        compact,\n        compact.takeIf { it.length == 8 }?.chunked(4)?.joinToString("-"),\n        compact.takeIf { it.length == 10 }?.chunked(5)?.joinToString("-"),\n        value.trim().uppercase().replace(Regex("[^A-Z0-9-]"), ""),\n    ).filterNotNull().filter { it.length in 6..32 }\n}'''
assert old_norm in k
k=k.replace(old_norm,new_norm,1)
repo.write_text(k)

test=root/'MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/HouseholdInvitationContractTest.kt'
t=test.read_text()
if 'compactInvitationCodeReceivesCanonicalSeparator' not in t:
    insert='''\n    @Test\n    fun compactInvitationCodeReceivesCanonicalSeparator() {\n        assertEquals("ABCDE-23456", normalizeHouseholdInvitationCode("abcde23456"))\n        assertEquals(true, householdInvitationLookupCandidates("ABCDE 23456").contains("ABCDE-23456"))\n    }\n'''
    t=t.replace('\n}',insert+'\n}',1)
test.write_text(t)

for rel in ['MyStudyCompanion/firestore.rules','MyStudyCompanionWeb/firestore.rules']:
    p=root/rel;r=p.read_text()
    r=r.replace("return !exists(userPath(uid)) ||\n        get(userPath(uid)).data.householdId == householdId;", "return !exists(userPath(uid)) ||\n        !('householdId' in get(userPath(uid)).data) ||\n        get(userPath(uid)).data.householdId == '' ||\n        get(userPath(uid)).data.householdId == householdId;")
    r=r.replace("allow update: if signedIn() && request.auth.uid == uid &&\n        request.resource.data.householdId == resource.data.householdId &&\n        validUserDocument(uid);", "allow update: if signedIn() && request.auth.uid == uid &&\n        (\n          request.resource.data.householdId == resource.data.householdId ||\n          (\n            (!('householdId' in resource.data) || resource.data.householdId == '') &&\n            request.resource.data.householdId.size() > 0\n          )\n        ) &&\n        validUserDocument(uid);")
    p.write_text(r)

fs=root/'MyStudyCompanionWeb/firebase-sync.js';f=fs.read_text()
append=r'''

export function normalizeHouseholdInvitationCode(value){
  const compact=String(value||"").trim().toUpperCase().replace(/[^A-Z0-9]/g,"");
  if(compact.length<6||compact.length>32)throw new Error("Enter a valid household invitation code.");
  if(compact.length===8)return `${compact.slice(0,4)}-${compact.slice(4)}`;
  if(compact.length===10)return `${compact.slice(0,5)}-${compact.slice(5)}`;
  return compact;
}
export async function householdStatus(){
  if(!currentUser)return {signedIn:false,householdId:"",role:"",displayName:""};
  const snapshot=await modules.getDoc(modules.doc(db,"users",currentUser.uid));
  const data=snapshot.data()||{};return {signedIn:true,householdId:data.householdId||"",role:data.role||"",displayName:data.displayName||currentUser.displayName||""};
}
export async function validateHouseholdInvitation(value){
  if(!currentUser)throw new Error("Sign in with Google first.");
  const canonical=normalizeHouseholdInvitationCode(value),compact=canonical.replace(/-/g,"");
  const candidates=[...new Set([canonical,compact,compact.length===8?`${compact.slice(0,4)}-${compact.slice(4)}`:null,compact.length===10?`${compact.slice(0,5)}-${compact.slice(5)}`:null].filter(Boolean))];
  for(const code of candidates){const snapshot=await modules.getDoc(modules.doc(db,"householdInvites",code));if(snapshot.exists()){const data=snapshot.data(),seconds=Number(data.expiresAtEpochSeconds||0);if(data.status!=="active")throw new Error("That invitation has already been used or cancelled.");if(seconds&&seconds<=Math.floor(Date.now()/1000))throw new Error("That invitation has expired. Ask the organizer for a new one.");return {code,householdId:data.householdId||""};}}
  throw new Error("That invitation code was not found. Check every character or ask the organizer for a fresh code.");
}
'''
if 'export function normalizeHouseholdInvitationCode' not in f:f+=append
fs.write_text(f)

app=root/'MyStudyCompanionWeb/app.js';a=app.read_text()
a=a.replace('import {configured,connect,restoreSession,pull,push} from "./firebase-sync.js?v=0145";','import {configured,connect,restoreSession,pull,push,householdStatus,validateHouseholdInvitation} from "./firebase-sync.js?v=0154";')
a=a.replace('import {createWorkbookEngine,eventBook,familyBook} from "./workbook.js?v=0140";','import {createWorkbookEngine,eventBook,familyBook} from "./workbook.js?v=0154";')
if 'async function refreshWebHouseholdStatus' not in a:
    block=r'''

async function refreshWebHouseholdStatus(){
  const status=document.getElementById("familyHouseholdStatus"),inviteStatus=document.getElementById("familyInviteStatus"),join=document.getElementById("joinFamilyButton"),create=document.getElementById("createFamilyButton"),createInvite=document.getElementById("createFamilyInviteButton");
  try{const household=await householdStatus();if(!household.signedIn){status.textContent="Sign in to load household members, roles, invitations, and shared family settings.";return;}if(household.householdId){status.textContent=`Connected to household ${household.householdId} • ${household.role||"member"}`;join.classList.add("hidden");create.classList.add("hidden");createInvite.classList.toggle("hidden",!["owner","organizer"].includes(household.role));inviteStatus.textContent="Household progress and workbook work synchronize with this Google account.";}else{status.textContent="This Google account is not linked to a household yet.";join.classList.remove("hidden");create.classList.remove("hidden");createInvite.classList.add("hidden");inviteStatus.textContent="For a first-time join, use the signed Android app so the correct child, preteen, teen, or adult profile and existing saved work are linked automatically.";}}
  catch(error){status.textContent=error.message;}
}
const joinFamilyButton=document.getElementById("joinFamilyButton");
if(joinFamilyButton)joinFamilyButton.onclick=async()=>{const output=document.getElementById("familyInviteStatus"),input=document.getElementById("familyInviteCode");try{joinFamilyButton.disabled=true;const invitation=await validateHouseholdInvitation(input.value);output.textContent=`Code ${invitation.code} is active. Finish the one-time join in the Android app so the correct study group and saved profile are migrated automatically.`;}catch(error){output.textContent=error.message;}finally{joinFamilyButton.disabled=false;}};
document.getElementById("createFamilyButton")?.addEventListener("click",()=>{document.getElementById("familyInviteStatus").textContent="Create the household in the Android app, where Firebase can create the owner profile, role, and automatic study-group mapping atomically.";});
document.getElementById("createFamilyInviteButton")?.addEventListener("click",()=>{document.getElementById("familyInviteStatus").textContent="Create and share a fresh one-time invitation from the Android Family Hub.";});
refreshWebHouseholdStatus();
'''
    a+=block
app.write_text(a)

for rel in ['MyStudyCompanion/app/build.gradle.kts','MyStudyCompanion/wear/build.gradle.kts']:
    p=root/rel;s=p.read_text()
    if 'app/build.gradle' in rel:
        s=re.sub(r'versionCode\s*=\s*36', 'versionCode = 37', s)
        s=s.replace('0.15.3-private-alpha-professional-workbook-assets','0.15.4-private-alpha-web-household-parity')
    else:
        s=re.sub(r'versionCode\s*=\s*360153001', 'versionCode = 360154001', s)
        s=s.replace('0.15.3-wear-private-alpha-professional-workbook-assets','0.15.4-wear-private-alpha-web-household-parity')
    p.write_text(s)

sw=root/'MyStudyCompanionWeb/sw.js';s=sw.read_text();s=re.sub(r'msc-web-v0153-professional-workbook-assets-v1','msc-web-v0154-professional-workbook-household-v1',s);sw.write_text(s)
appear=root/'MyStudyCompanionWeb/appearance.test.mjs';s=appear.read_text();s=s.replace('msc-web-v0153-professional-workbook-assets-v1','msc-web-v0154-professional-workbook-household-v1');appear.write_text(s)
for pattern in ('*.rej','*.orig','*.tmp'):
    for p in (root/'MyStudyCompanionWeb').rglob(pattern): p.unlink()
(root/'MyStudyCompanionWeb/workbook-professional.test.mjs').write_text('''import test from "node:test";\nimport assert from "node:assert/strict";\nimport {readFileSync} from "node:fs";\nconst workbook=readFileSync(new URL("./workbook.js",import.meta.url),"utf8");\ntest("PWA workbook uses stored professional assets and masks",()=>{\n  for(const marker of ["loadProfessionalWorkbookAsset","professionalPrintData","regionMaskData","difference-changed.webp","drawing-step-1.webp","renderSavedWork"]) assert.ok(workbook.includes(marker),marker);\n  assert.ok(workbook.includes("drawProfessionalColorCanvas"));\n});\n''')
(root/'MyStudyCompanionWeb/household-invitation.test.mjs').write_text('''import test from "node:test";\nimport assert from "node:assert/strict";\nimport {normalizeHouseholdInvitationCode} from "./firebase-sync.js";\ntest("copied household codes normalize to the stored form",()=>{\n  assert.equal(normalizeHouseholdInvitationCode("abcde23456"),"ABCDE-23456");\n  assert.equal(normalizeHouseholdInvitationCode(" AB12 CD34 "),"AB12-CD34");\n});\n''')
print('patched 0.15.4 web household parity')
