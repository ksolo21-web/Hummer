using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    public static class HavenlineSceneAuthoring
    {
        private const string Generated = "Assets/Havenline/Generated";
        private const string Art = "Assets/Havenline/Art/Reference";

        [MenuItem("HAVENLINE Reference/Author Exact Frozen Outpost Scene")]
        public static void Author()
        {
            Directory.CreateDirectory("Assets/Havenline/Scenes"); Directory.CreateDirectory(Generated);
            EnsureUrp();
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            var root = new GameObject("HAVENLINE_FROZEN_OUTPOST_REFERENCE");
            BuildSnowIsland(root.transform); BuildBounds(root.transform); BuildLighting(root.transform);
            var input = new GameObject("Input").AddComponent<HavenlineInputRouter>();
            var player = BuildPlayer(root.transform, input);
            BuildCamera(root.transform, player.transform);
            var furnace = BuildFurnace(root.transform);
            BuildStorage(root.transform); BuildCampfire(root.transform); BuildTents(root.transform); BuildScenery(root.transform); BuildResources(root.transform);
            var helper = BuildHelper(root.transform);
            BuildBarricade(root.transform, Reference.NorthBarricade, 0f, "NorthBarricade");
            BuildBarricade(root.transform, Reference.SouthBarricade, 180f, "SouthBarricade");
            BuildForestGate(root.transform);
            var enemyPrefab = BuildEnemyPrefab();
            var director = new GameObject("GameplayDirector").AddComponent<HavenlineGameDirector>();
            director.transform.SetParent(root.transform); director.Configure(enemyPrefab, helper, furnace);
            BuildHud(root.transform, player, director); BuildSnowfall(root.transform); root.AddComponent<HavenlinePerformance>();
            EditorSceneManager.SaveScene(scene, Reference.ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(Reference.ScenePath, true) };
            AssetDatabase.SaveAssets(); AssetDatabase.Refresh(); ValidateAuthoredScene();
        }

        public static void ValidateAuthoredScene()
        {
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            Require<HavenlinePlayerController>(scene, 1, "player"); Require<HavenlineCameraRig>(scene, 1, "camera rig");
            Require<HavenlineFurnace>(scene,1,"furnace"); Require<HavenlineHelper>(scene,1,"survivor/helper");
            Require<HavenlineBarricade>(scene,2,"barricades", true); Require<HavenlineResourceNode>(scene,10,"resource nodes",true);
            var camera = Find<HavenlineCameraRig>(scene).GetComponent<Camera>();
            if (!camera.orthographic || Mathf.Abs(camera.orthographicSize - Reference.CameraSize) > 0.001f) throw new InvalidOperationException("Camera contract failed.");
            var player = Find<HavenlinePlayerController>(scene);
            if (Vector3.Distance(player.transform.position, Reference.PlayerSpawn) > 0.01f) throw new InvalidOperationException("Player spawn contract failed.");
        }

        private static HavenlinePlayerController BuildPlayer(Transform parent, HavenlineInputRouter input)
        {
            var root = new GameObject("Player"); root.transform.SetParent(parent); root.transform.position = Reference.PlayerSpawn;
            var controller = root.AddComponent<CharacterController>(); controller.height=1.75f; controller.radius=.34f; controller.center=new Vector3(0,.88f,0);
            var inventory = root.AddComponent<HavenlineInventory>();
            var visual = AddModel(root.transform, FindAsset("Superhero_Male_FullBody.gltf"), 1.78f, "PlayerVisual");
            var animator = visual.gameObject.AddComponent<HavenlineActorAnimator>();
            var carry = new GameObject("VisibleCarry").transform; carry.SetParent(visual); carry.localPosition=new Vector3(0,1.05f,-.32f);
            var pack = AddModel(carry, FindAsset("Backpack.fbx"), .58f, "Backpack"); pack.localRotation=Quaternion.Euler(0,180,0); carry.gameObject.SetActive(false);
            inventory.Configure(carry);
            var player = root.AddComponent<HavenlinePlayerController>(); player.Configure(input, visual, animator); return player;
        }

        private static Camera BuildCamera(Transform parent, Transform target)
        {
            var go = new GameObject("ReferenceCamera"); go.transform.SetParent(parent); go.tag="MainCamera";
            var camera = go.AddComponent<Camera>(); camera.orthographic=true; camera.orthographicSize=Reference.CameraSize; camera.nearClipPlane=.15f; camera.farClipPlane=120f;
            camera.clearFlags=CameraClearFlags.SolidColor; camera.backgroundColor=new Color(.075f,.12f,.17f); camera.allowHDR=true; camera.allowMSAA=true;
            go.AddComponent<AudioListener>(); var rig=go.AddComponent<HavenlineCameraRig>(); rig.Configure(target); return camera;
        }

        private static HavenlineFurnace BuildFurnace(Transform parent)
        {
            var root=new GameObject("Furnace"); root.transform.SetParent(parent); root.transform.position=Reference.Furnace;
            AddModel(root.transform,FindAsset("Fireplace.glb"),2.25f,"FurnaceVisual");
            var collider=root.AddComponent<SphereCollider>(); collider.radius=1.8f; collider.isTrigger=true;
            var ring=new GameObject("WarmthBoundary"); ring.transform.SetParent(root.transform,false); ring.transform.localPosition=new Vector3(0,.035f,0);
            ring.AddComponent<MeshFilter>().sharedMesh=CreateRingMesh(); ring.AddComponent<MeshRenderer>().sharedMaterial=Material("Warmth",new Color(1f,.28f,.045f,.26f),true);
            var lightGo=new GameObject("FurnaceLight"); lightGo.transform.SetParent(root.transform,false); lightGo.transform.localPosition=new Vector3(0,1.25f,0);
            var light=lightGo.AddComponent<Light>(); light.type=LightType.Point; light.color=new Color(1f,.34f,.08f); light.shadows=LightShadows.Soft;
            var particles=CreateFire(root.transform); var furnace=root.AddComponent<HavenlineFurnace>(); furnace.Configure(ring.transform,light,particles); return furnace;
        }

        private static void BuildStorage(Transform parent)
        {
            var root=new GameObject("SupplyStorage"); root.transform.SetParent(parent); root.transform.position=Reference.Storage;
            for(var i=0;i<5;i++){var log=AddModel(root.transform,FindAsset("WoodLog.fbx"),.38f,$"StoredLog_{i}"); log.localPosition=new Vector3((i%3-.9f)*.42f,(i/3)*.28f,(i%2)*.22f); log.localRotation=Quaternion.Euler(0,i*31f,90);}
            var pack=AddModel(root.transform,FindAsset("Backpack.fbx"),.72f,"SupplyPack"); pack.localPosition=new Vector3(-.65f,.2f,.35f);
        }
        private static void BuildCampfire(Transform parent)
        { var root=new GameObject("Campfire"); root.transform.SetParent(parent); root.transform.position=Reference.Campfire; AddModel(root.transform,FindAsset("Bonfire.fbx"),1.05f,"CampfireVisual"); CreateFire(root.transform); }
        private static void BuildTents(Transform parent)
        { BuildProp(parent,"LeftShelter",FindAsset("Tent.fbx"),Reference.TentLeft,2.6f,15f); BuildProp(parent,"RightShelter",FindAsset("Tent.fbx"),Reference.TentRight,2.6f,-15f); }

        private static HavenlineHelper BuildHelper(Transform parent)
        {
            var root=new GameObject("RescueableSurvivor"); root.transform.SetParent(parent); root.transform.position=Reference.Survivor;
            var cc=root.AddComponent<CharacterController>(); cc.height=1.72f; cc.radius=.34f; cc.center=new Vector3(0,.86f,0);
            var inv=root.AddComponent<HavenlineInventory>(); var visual=AddModel(root.transform,FindAsset("Superhero_Female_FullBody.gltf"),1.72f,"SurvivorVisual");
            var animator=visual.gameObject.AddComponent<HavenlineActorAnimator>(); var carry=new GameObject("VisibleCarry").transform; carry.SetParent(visual); carry.localPosition=new Vector3(0,1.0f,-.3f);
            AddModel(carry,FindAsset("Backpack.fbx"),.52f,"HelperPack").localRotation=Quaternion.Euler(0,180,0); carry.gameObject.SetActive(false); inv.Configure(carry);
            var helper=root.AddComponent<HavenlineHelper>(); helper.Configure(visual,animator); return helper;
        }

        private static HavenlineEnemy BuildEnemyPrefab()
        {
            Directory.CreateDirectory(Generated+"/Prefabs");
            var root=new GameObject("WolfEnemy"); root.SetActive(false); var cc=root.AddComponent<CharacterController>(); cc.height=.95f; cc.radius=.35f; cc.center=new Vector3(0,.48f,0);
            var visual=AddModel(root.transform,FindAsset("Wolf.glb"),.95f,"WolfVisual"); var animator=visual.gameObject.AddComponent<HavenlineActorAnimator>(); var enemy=root.AddComponent<HavenlineEnemy>(); enemy.Configure(visual,animator);
            var prefab=PrefabUtility.SaveAsPrefabAsset(root,Generated+"/Prefabs/WolfEnemy.prefab"); UnityEngine.Object.DestroyImmediate(root); return prefab.GetComponent<HavenlineEnemy>();
        }

        private static void BuildBarricade(Transform parent,Vector3 position,float yaw,string name)
        {
            var root=new GameObject(name); root.transform.SetParent(parent); root.transform.position=position; root.transform.rotation=Quaternion.Euler(0,yaw,0);
            for(var i=-4;i<=4;i++){var log=AddModel(root.transform,FindAsset("WoodLog.fbx"),.64f,$"Log_{i}"); log.localPosition=new Vector3(i*.62f,.55f+Mathf.Abs(i%2)*.14f,0); log.localRotation=Quaternion.Euler(0,0,90);}
            var collider=root.AddComponent<BoxCollider>(); collider.size=new Vector3(6.2f,1.25f,.75f); collider.center=new Vector3(0,.62f,0); root.AddComponent<HavenlineBarricade>();
        }
        private static void BuildForestGate(Transform parent)
        {
            var root=new GameObject("ForestLineGate"); root.transform.SetParent(parent); root.transform.position=Reference.ForestGate;
            for(var side=-1;side<=1;side+=2) for(var y=0;y<3;y++){var log=AddModel(root.transform,FindAsset("WoodLog.fbx"),.75f,$"GateLog_{side}_{y}"); log.localPosition=new Vector3(side*2.2f,.45f+y*.65f,0); log.localRotation=Quaternion.Euler(0,0,90);}
        }

        private static void BuildResources(Transform parent)
        {
            var index=0; foreach(var p in Reference.WoodNodes){var root=BuildProp(parent,$"WoodNode_{index++}",FindAsset(index%2==0?"Pine_2.fbx":"Pine_3.fbx"),p,4.2f,index*23f); root.gameObject.AddComponent<HavenlineResourceNode>().Configure(ResourceKind.Wood,18);}
            index=0; foreach(var p in Reference.StoneNodes){var root=BuildProp(parent,$"StoneNode_{index++}",FindAsset(index%2==0?"Rock_Medium_2.fbx":"Rock_Medium_3.fbx"),p,1.45f,index*41f); root.gameObject.AddComponent<HavenlineResourceNode>().Configure(ResourceKind.Stone,14);}
        }
        private static void BuildScenery(Transform parent)
        {
            var positions=new[]{new Vector3(-12,0,10),new Vector3(12,0,10),new Vector3(-12,0,-9),new Vector3(12,0,-9),new Vector3(-5,0,-13),new Vector3(5,0,-13)};
            for(var i=0;i<positions.Length;i++) BuildProp(parent,$"SceneryPine_{i}",FindAsset(i%2==0?"Pine_2.fbx":"Pine_3.fbx"),positions[i],5.0f,i*57f);
        }

        private static void BuildHud(Transform parent,HavenlinePlayerController player,HavenlineGameDirector director)
        {
            var canvasGo=new GameObject("HUD",typeof(Canvas),typeof(CanvasScaler),typeof(GraphicRaycaster),typeof(HavenlineSafeArea)); canvasGo.transform.SetParent(parent);
            var canvas=canvasGo.GetComponent<Canvas>(); canvas.renderMode=RenderMode.ScreenSpaceOverlay; canvas.sortingOrder=20;
            var scaler=canvasGo.GetComponent<CanvasScaler>(); scaler.uiScaleMode=CanvasScaler.ScaleMode.ScaleWithScreenSize; scaler.referenceResolution=new Vector2(1920,1080); scaler.matchWidthOrHeight=.5f;
            var resources=TextCard(canvasGo.transform,"Resources",new Vector2(24,-24),new Vector2(680,62),TextAnchor.MiddleLeft,24);
            var furnace=TextCard(canvasGo.transform,"Furnace",new Vector2(-24,-24),new Vector2(520,62),TextAnchor.MiddleRight,24,true);
            var objective=TextCard(canvasGo.transform,"Objective",new Vector2(0,-98),new Vector2(760,94),TextAnchor.MiddleCenter,30,false,true);
            var helper=TextCard(canvasGo.transform,"Helper",new Vector2(24,102),new Vector2(430,52),TextAnchor.MiddleLeft,22,false,false,true);
            var wave=TextCard(canvasGo.transform,"Wave",new Vector2(-24,102),new Vector2(360,52),TextAnchor.MiddleRight,22,true,false,true);
            var bar=Bar(canvasGo.transform); JoystickArt(canvasGo.transform); DashArt(canvasGo.transform);
            var hud=canvasGo.AddComponent<HavenlineHud>(); hud.Configure(resources,objective,furnace,helper,wave,bar,player,director);
        }

        private static Text TextCard(Transform parent,string name,Vector2 offset,Vector2 size,TextAnchor alignment,int fontSize,bool anchorRight=false,bool anchorCenter=false,bool anchorBottom=false)
        {
            var panel=new GameObject(name+"Card",typeof(Image)); panel.transform.SetParent(parent,false); var rect=(RectTransform)panel.transform;
            var anchor=anchorCenter?new Vector2(.5f,1f):new Vector2(anchorRight?1f:0f,anchorBottom?0f:1f); rect.anchorMin=rect.anchorMax=anchor; rect.pivot=new Vector2(anchorCenter ? .5f : (anchorRight ? 1f : 0f), anchorBottom ? 0f : 1f); rect.anchoredPosition=offset; rect.sizeDelta=size;
            panel.GetComponent<Image>().color=new Color(.025f,.055f,.085f,.82f);
            var textGo=new GameObject(name+"Text",typeof(Text)); textGo.transform.SetParent(panel.transform,false); var tr=(RectTransform)textGo.transform; tr.anchorMin=Vector2.zero; tr.anchorMax=Vector2.one; tr.offsetMin=new Vector2(18,8); tr.offsetMax=new Vector2(-18,-8);
            var text=textGo.GetComponent<Text>(); text.font=Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); text.fontSize=fontSize; text.alignment=alignment; text.color=new Color(.91f,.97f,1f); text.horizontalOverflow=HorizontalWrapMode.Wrap; text.verticalOverflow=VerticalWrapMode.Truncate; return text;
        }
        private static Image Bar(Transform parent)
        {
            var bg=new GameObject("WarmthBarBackground",typeof(Image)); bg.transform.SetParent(parent,false); var r=(RectTransform)bg.transform; r.anchorMin=r.anchorMax=new Vector2(.5f,1f); r.pivot=new Vector2(.5f,1f); r.anchoredPosition=new Vector2(0,-196); r.sizeDelta=new Vector2(420,12); bg.GetComponent<Image>().color=new Color(.08f,.16f,.22f,.85f);
            var fill=new GameObject("WarmthBar",typeof(Image)); fill.transform.SetParent(bg.transform,false); var fr=(RectTransform)fill.transform; fr.anchorMin=Vector2.zero; fr.anchorMax=Vector2.one; fr.offsetMin=fr.offsetMax=Vector2.zero; var image=fill.GetComponent<Image>(); image.color=new Color(1f,.33f,.08f,.95f); image.type=Image.Type.Filled; image.fillMethod=Image.FillMethod.Horizontal; return image;
        }
        private static void JoystickArt(Transform parent)
        { Circle(parent,"JoystickBase",new Vector2(190,185),145,new Color(.4f,.7f,.9f,.16f),false); Circle(parent,"JoystickKnob",new Vector2(190,185),62,new Color(.7f,.9f,1f,.32f),false); }
        private static void DashArt(Transform parent)
        { Circle(parent,"SprintButton",new Vector2(-190,185),105,new Color(.12f,.52f,.86f,.42f),true); var t=TextCard(parent,"Sprint",new Vector2(-190,185),new Vector2(180,70),TextAnchor.MiddleCenter,24,true,false,true); t.text="SPRINT"; t.transform.parent.GetComponent<Image>().color=Color.clear; }
        private static void Circle(Transform parent,string name,Vector2 offset,float size,Color color,bool right)
        { var go=new GameObject(name,typeof(Image)); go.transform.SetParent(parent,false); var r=(RectTransform)go.transform; r.anchorMin=r.anchorMax=new Vector2(right?1:0,0); r.pivot=new Vector2(right?1:0,0); r.anchoredPosition=offset; r.sizeDelta=Vector2.one*size; go.GetComponent<Image>().color=color; go.GetComponent<Image>().raycastTarget=false; }

        private static Transform BuildProp(Transform parent,string name,string asset,Vector3 position,float height,float yaw)
        { var root=new GameObject(name).transform; root.SetParent(parent); root.position=position; root.rotation=Quaternion.Euler(0,yaw,0); AddModel(root,asset,height,name+"Visual"); return root; }
        private static Transform AddModel(Transform parent,string assetPath,float targetHeight,string name)
        {
            var asset=AssetDatabase.LoadAssetAtPath<GameObject>(assetPath); if(asset==null) throw new FileNotFoundException("Unity did not import reference model",assetPath);
            var instance=(GameObject)PrefabUtility.InstantiatePrefab(asset); instance.name=name; instance.transform.SetParent(parent,false);
            var renderers=instance.GetComponentsInChildren<Renderer>(true); if(renderers.Length>0){var bounds=renderers[0].bounds; foreach(var r in renderers.Skip(1)) bounds.Encapsulate(r.bounds); if(bounds.size.y>.001f) instance.transform.localScale*=targetHeight/bounds.size.y;}
            return instance.transform;
        }
        private static string FindAsset(string fileName)
        {
            var match=Directory.EnumerateFiles(Art,fileName,SearchOption.AllDirectories).FirstOrDefault();
            if(match==null) throw new FileNotFoundException($"Reference asset not found: {fileName}"); return match.Replace('\\','/');
        }

        private static GameObject BuildSnowIsland(Transform parent)
        {
            var go=new GameObject("SnowIsland"); go.transform.SetParent(parent); var mesh=CreateSnowMesh(); go.AddComponent<MeshFilter>().sharedMesh=mesh; go.AddComponent<MeshRenderer>().sharedMaterial=Material("Snow",new Color(.72f,.83f,.9f),false); go.AddComponent<MeshCollider>().sharedMesh=mesh; return go;
        }
        private static Mesh CreateSnowMesh()
        {
            const int xCount=17,zCount=19; var vertices=new Vector3[xCount*zCount]; var uv=new Vector2[vertices.Length]; var triangles=new int[(xCount-1)*(zCount-1)*6];
            for(var z=0;z<zCount;z++) for(var x=0;x<xCount;x++){var px=Mathf.Lerp(-16,16,x/(float)(xCount-1)); var pz=Mathf.Lerp(-18,18,z/(float)(zCount-1)); var edge=Mathf.Max(Mathf.Abs(px)/16f,Mathf.Abs(pz)/18f); var y=.08f*Mathf.Sin(px*.52f)*Mathf.Cos(pz*.43f)-Mathf.Pow(Mathf.Max(0,edge-.82f),2)*4f; var i=z*xCount+x; vertices[i]=new Vector3(px,y,pz); uv[i]=new Vector2(x/(float)(xCount-1),z/(float)(zCount-1));}
            var t=0; for(var z=0;z<zCount-1;z++) for(var x=0;x<xCount-1;x++){var i=z*xCount+x; triangles[t++]=i;triangles[t++]=i+xCount;triangles[t++]=i+1;triangles[t++]=i+1;triangles[t++]=i+xCount;triangles[t++]=i+xCount+1;}
            var mesh=new Mesh{name="HAVENLINE_SnowIsland"}; mesh.vertices=vertices;mesh.uv=uv;mesh.triangles=triangles;mesh.RecalculateNormals();mesh.RecalculateBounds(); return mesh;
        }
        private static void BuildBounds(Transform parent)
        {
            void Wall(string name,Vector3 pos,Vector3 size){var go=new GameObject(name);go.transform.SetParent(parent);go.transform.position=pos;go.AddComponent<BoxCollider>().size=size;}
            Wall("WestBoundary",new Vector3(-Reference.BoundX-.4f,1f,0),new Vector3(.8f,4f,Reference.BoundZ*2+3)); Wall("EastBoundary",new Vector3(Reference.BoundX+.4f,1f,0),new Vector3(.8f,4f,Reference.BoundZ*2+3));
            Wall("NorthBoundary",new Vector3(0,1f,-Reference.BoundZ-.4f),new Vector3(Reference.BoundX*2+3,4f,.8f)); Wall("SouthBoundary",new Vector3(0,1f,Reference.BoundZ+.4f),new Vector3(Reference.BoundX*2+3,4f,.8f));
        }
        private static void BuildLighting(Transform parent)
        {
            var sun=new GameObject("WinterSun");sun.transform.SetParent(parent);sun.transform.rotation=Quaternion.Euler(47,-32,0);var light=sun.AddComponent<Light>();light.type=LightType.Directional;light.color=new Color(.78f,.88f,1f);light.intensity=1.25f;light.shadows=LightShadows.Soft;
            RenderSettings.ambientMode=AmbientMode.Trilight;RenderSettings.ambientSkyColor=new Color(.24f,.36f,.48f);RenderSettings.ambientEquatorColor=new Color(.12f,.19f,.27f);RenderSettings.ambientGroundColor=new Color(.045f,.07f,.09f);RenderSettings.fog=true;RenderSettings.fogMode=FogMode.ExponentialSquared;RenderSettings.fogDensity=.012f;RenderSettings.fogColor=new Color(.16f,.25f,.34f);
        }
        private static ParticleSystem CreateFire(Transform parent)
        {
            var go=new GameObject("FireParticles");go.transform.SetParent(parent,false);go.transform.localPosition=new Vector3(0,.45f,0);var ps=go.AddComponent<ParticleSystem>();var main=ps.main;main.startLifetime=.75f;main.startSpeed=.9f;main.startSize=.5f;main.maxParticles=120;main.startColor=new ParticleSystem.MinMaxGradient(new Color(1f,.16f,.015f),new Color(1f,.72f,.08f));var emission=ps.emission;emission.rateOverTime=24;var shape=ps.shape;shape.shapeType=ParticleSystemShapeType.Cone;shape.angle=12;shape.radius=.18f;return ps;
        }
        private static void BuildSnowfall(Transform parent)
        {
            var go=new GameObject("Snowfall");go.transform.SetParent(parent);var ps=go.AddComponent<ParticleSystem>();var main=ps.main;main.startLifetime=7;main.startSpeed=.75f;main.startSize=.065f;main.maxParticles=1800;main.simulationSpace=ParticleSystemSimulationSpace.World;main.startColor=new Color(1,1,1,.8f);var emission=ps.emission;emission.rateOverTime=170;var shape=ps.shape;shape.shapeType=ParticleSystemShapeType.Box;shape.scale=new Vector3(30,1,28);go.AddComponent<HavenlineSnowfall>();
        }
        private static Mesh CreateRingMesh()
        {
            const int segments=96;var vertices=new Vector3[segments*2];var triangles=new int[segments*6];for(var i=0;i<segments;i++){var a=i*Mathf.PI*2/segments;var d=new Vector3(Mathf.Cos(a),0,Mathf.Sin(a));vertices[i*2]=d*.96f;vertices[i*2+1]=d;var n=(i+1)%segments;var t=i*6;triangles[t]=i*2;triangles[t+1]=n*2;triangles[t+2]=i*2+1;triangles[t+3]=i*2+1;triangles[t+4]=n*2;triangles[t+5]=n*2+1;}var m=new Mesh{name="WarmthBoundaryRing"};m.vertices=vertices;m.triangles=triangles;m.RecalculateNormals();return m;
        }
        private static Material Material(string name,Color color,bool transparent)
        {
            var path=$"{Generated}/{name}.mat";var material=AssetDatabase.LoadAssetAtPath<Material>(path);if(material!=null)return material;var shader=Shader.Find("Universal Render Pipeline/Lit")??Shader.Find("Standard");material=new Material(shader){name=name,color=color};if(transparent){material.SetFloat("_Surface",1);material.SetFloat("_ZWrite",0);material.renderQueue=3000;}AssetDatabase.CreateAsset(material,path);return material;
        }
        private static void EnsureUrp()
        {
            Directory.CreateDirectory(Generated+"/RenderPipeline");var rendererPath=Generated+"/RenderPipeline/HavenlineRenderer.asset";var pipelinePath=Generated+"/RenderPipeline/HavenlineURP.asset";
            var renderer=AssetDatabase.LoadAssetAtPath<UniversalRendererData>(rendererPath);if(renderer==null){renderer=ScriptableObject.CreateInstance<UniversalRendererData>();AssetDatabase.CreateAsset(renderer,rendererPath);}
            var pipeline=AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(pipelinePath);if(pipeline==null){pipeline=UniversalRenderPipelineAsset.Create(renderer);AssetDatabase.CreateAsset(pipeline,pipelinePath);}GraphicsSettings.defaultRenderPipeline=pipeline;QualitySettings.renderPipeline=pipeline;QualitySettings.shadowDistance=45;QualitySettings.vSyncCount=0;
        }
        private static T Find<T>(Scene scene) where T:Component => scene.GetRootGameObjects().SelectMany(r=>r.GetComponentsInChildren<T>(true)).First();
        private static void Require<T>(Scene scene,int count,string label,bool atLeast=false) where T:Component
        {var actual=scene.GetRootGameObjects().SelectMany(r=>r.GetComponentsInChildren<T>(true)).Count();if(atLeast?actual<count:actual!=count)throw new InvalidOperationException($"Reference scene requires {(atLeast?"at least ":"")}{count} {label}; found {actual}.");}
    }
}
