using System.Collections;
using System.Collections.Generic;
using NUnit.Framework;
using UnityEngine;
using UnityEngine.TestTools;

namespace Havenline.Tests
{
    public sealed class HavenlineTestAutomaticTarget : HavenlineInteractable
    {
        public int TickCount { get; private set; }
        public override AutomaticActionKind ActionKind => AutomaticActionKind.GatherWood;
        public override bool CanInteract(HavenlinePlayerController actor) => actor != null;
        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime) => TickCount++;
    }

    public sealed class HavenlineOpeningLoopPlayModeTests
    {
        private readonly List<GameObject> created = new();

        [UnitySetUp]
        public IEnumerator SetUp()
        {
            HavenlineSave.ResetAll();
            yield return null;
        }

        [UnityTearDown]
        public IEnumerator TearDown()
        {
            foreach (var gameObject in created)
            {
                if (gameObject != null)
                    Object.Destroy(gameObject);
            }
            created.Clear();
            yield return null;
            HavenlineSave.ResetAll();
        }

        [UnityTest]
        public IEnumerator NearbyTargetStartsAutomaticallyWithoutAnActionButton()
        {
            var floor = Create("Floor");
            floor.transform.position = Reference.PlayerSpawn + new Vector3(0f, -0.58f, 0f);
            var floorCollider = floor.AddComponent<BoxCollider>();
            floorCollider.size = new Vector3(12f, 1f, 12f);

            var playerObject = Create("Player");
            playerObject.transform.position = Reference.PlayerSpawn;
            var player = playerObject.AddComponent<HavenlinePlayerController>();
            player.Configure(null, playerObject.transform, null);

            var targetObject = Create("AutomaticWoodTarget");
            targetObject.transform.position = Reference.PlayerSpawn + new Vector3(1f, 0f, 0f);
            var target = targetObject.AddComponent<HavenlineTestAutomaticTarget>();

            yield return new WaitForSeconds(0.25f);

            Assert.That(player.AutomaticActions.CurrentAction, Is.EqualTo(AutomaticActionKind.GatherWood));
            Assert.That(target.TickCount, Is.GreaterThan(0));
        }

        [Test]
        public void InventoryEnforcesCapacityAndPreservesResourceKinds()
        {
            var inventoryObject = Create("Inventory");
            var inventory = inventoryObject.AddComponent<HavenlineInventory>();
            inventory.Configure(null, null, 3);

            Assert.That(inventory.Add(ResourceKind.Wood, 2), Is.EqualTo(2));
            Assert.That(inventory.Add(ResourceKind.Stone, 2), Is.EqualTo(1));
            Assert.That(inventory.Total, Is.EqualTo(3));
            Assert.That(inventory.IsFull, Is.True);
            Assert.That(inventory[ResourceKind.Wood], Is.EqualTo(2));
            Assert.That(inventory[ResourceKind.Stone], Is.EqualTo(1));
        }

        [Test]
        public void FurnaceReceivesOneResourceAtATimeAndUpgrades()
        {
            var inventoryObject = Create("DeliveryInventory");
            var inventory = inventoryObject.AddComponent<HavenlineInventory>();
            inventory.Configure(null, null, 40);
            inventory.Add(ResourceKind.Wood, 18);
            inventory.Add(ResourceKind.Stone, 6);

            var furnaceObject = Create("Furnace");
            var furnace = furnaceObject.AddComponent<HavenlineFurnace>();
            furnace.Configure(null, null, null);

            var before = inventory.Total;
            Assert.That(furnace.DepositOne(inventory), Is.True);
            Assert.That(inventory.Total, Is.EqualTo(before - 1));

            while (furnace.DepositOne(inventory)) { }

            Assert.That(furnace.Stored(ResourceKind.Wood), Is.EqualTo(18));
            Assert.That(furnace.Stored(ResourceKind.Stone), Is.EqualTo(6));
            Assert.That(furnace.Level, Is.GreaterThanOrEqualTo(2));
            Assert.That(furnace.WarmthRadius, Is.GreaterThan(4f));
        }

        [Test]
        public void FrozenSurvivorCompletesRescueAndBecomesAHelper()
        {
            var furnaceObject = Create("RescueFurnace");
            var furnace = furnaceObject.AddComponent<HavenlineFurnace>();
            furnace.Configure(null, null, null);
            furnace.Restore(new HavenlineFurnaceSnapshot
            {
                level = 2,
                wood = 18,
                stone = 6
            });

            var playerObject = Create("Rescuer");
            var player = playerObject.AddComponent<HavenlinePlayerController>();
            player.Configure(null, playerObject.transform, null);

            var helperObject = Create("FrozenHelper");
            var helper = helperObject.AddComponent<HavenlineHelper>();
            helper.Configure(helperObject.transform, null);

            Assert.That(helper.CanInteract(player), Is.True);
            helper.OnSelected(player);
            helper.TickInteraction(player, 1.1f);
            helper.TickInteraction(player, 1.2f);

            Assert.That(helper.State, Is.EqualTo(HelperState.Following));
        }

        [Test]
        public void DeliveredWoodAndStoneConstructTheDefenseInWorld()
        {
            var inventoryObject = Create("BuilderInventory");
            var inventory = inventoryObject.AddComponent<HavenlineInventory>();
            inventory.Configure(null, null, 20);
            inventory.Add(ResourceKind.Wood, 8);
            inventory.Add(ResourceKind.Stone, 3);

            var completed = Create("CompletedBarricade");
            completed.SetActive(false);
            var siteObject = Create("BarricadeSite");
            var site = siteObject.AddComponent<HavenlineConstructionSite>();
            site.Configure("playmode_barricade", 8, 3, new GameObject[0], completed, null);

            for (var index = 0; index < 16 && !site.IsBuilt; index++)
                site.ContributeForHelper(inventory, null, 1f);

            Assert.That(site.IsBuilt, Is.True);
            Assert.That(site.DeliveredWood, Is.EqualTo(8));
            Assert.That(site.DeliveredStone, Is.EqualTo(3));
            Assert.That(completed.activeSelf, Is.True);
            Assert.That(inventory.Total, Is.Zero);
        }

        private GameObject Create(string name)
        {
            var gameObject = new GameObject(name);
            created.Add(gameObject);
            return gameObject;
        }
    }
}
