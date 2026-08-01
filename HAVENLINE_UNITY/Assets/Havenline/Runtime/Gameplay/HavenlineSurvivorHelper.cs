using System.Linq;
using UnityEngine;
using UnityEngine.AI;

namespace Havenline
{
    [RequireComponent(typeof(NavMeshAgent), typeof(HavenlineInventory))]
    public sealed class HavenlineSurvivorHelper : MonoBehaviour
    {
        private enum HelperState
        {
            AwaitingRescue,
            SeekingResource,
            Gathering,
            ReturningToFurnace,
            WorkingAtFurnace
        }

        [SerializeField] private HavenlineFurnace furnace;
        [SerializeField] private HavenlineWarmthZone warmthZone;
        [SerializeField, Min(0.5f)] private float rescueHoldTime = 1.2f;
        [SerializeField, Min(0.1f)] private float retargetInterval = 0.75f;
        [SerializeField, Min(0.1f)] private float workPause = 0.35f;
        [SerializeField] private Animator animator;
        [SerializeField] private string rescuedTrigger = "Rescued";
        [SerializeField] private string speedParameter = "Speed";
        [SerializeField] private string workParameter = "Working";

        private NavMeshAgent _agent;
        private HavenlineInventory _inventory;
        private HelperState _state = HelperState.AwaitingRescue;
        private HavenlineResourceNode _targetResource;
        private float _rescueTimer;
        private float _nextRetargetTime;
        private float _workUntil;
        private int _rescuedHash;
        private int _speedHash;
        private int _workHash;

        public bool IsRescued => _state != HelperState.AwaitingRescue;

        private void Awake()
        {
            _agent = GetComponent<NavMeshAgent>();
            _inventory = GetComponent<HavenlineInventory>();
            _rescuedHash = Animator.StringToHash(rescuedTrigger);
            _speedHash = Animator.StringToHash(speedParameter);
            _workHash = Animator.StringToHash(workParameter);
            _agent.isStopped = true;
        }

        private void Update()
        {
            UpdateAnimation();

            switch (_state)
            {
                case HelperState.AwaitingRescue:
                    UpdateRescue();
                    break;
                case HelperState.SeekingResource:
                    UpdateSeekingResource();
                    break;
                case HelperState.Gathering:
                    UpdateGathering();
                    break;
                case HelperState.ReturningToFurnace:
                    UpdateReturning();
                    break;
                case HelperState.WorkingAtFurnace:
                    UpdateWorkingAtFurnace();
                    break;
            }
        }

        public void Configure(HavenlineFurnace targetFurnace, HavenlineWarmthZone targetWarmth)
        {
            furnace = targetFurnace;
            warmthZone = targetWarmth;
        }

        private void UpdateRescue()
        {
            if (warmthZone == null || !warmthZone.Contains(transform.position))
            {
                _rescueTimer = 0f;
                return;
            }

            _rescueTimer += Time.deltaTime;
            if (_rescueTimer < rescueHoldTime)
            {
                return;
            }

            _state = HelperState.SeekingResource;
            _agent.isStopped = false;
            if (animator != null)
            {
                animator.SetTrigger(_rescuedHash);
            }
        }

        private void UpdateSeekingResource()
        {
            if (_inventory.IsFull)
            {
                BeginReturn();
                return;
            }

            if (_targetResource == null || _targetResource.IsDepleted || Time.time >= _nextRetargetTime)
            {
                _targetResource = FindBestResource();
                _nextRetargetTime = Time.time + retargetInterval;
            }

            if (_targetResource == null)
            {
                BeginReturn();
                return;
            }

            _agent.SetDestination(_targetResource.transform.position);
            if (!_agent.pathPending && _agent.remainingDistance <= Mathf.Max(_agent.stoppingDistance, 1.25f))
            {
                _state = HelperState.Gathering;
            }
        }

        private void UpdateGathering()
        {
            if (_inventory.IsFull || _targetResource == null || _targetResource.IsDepleted)
            {
                BeginReturn();
                return;
            }

            if (Vector3.Distance(transform.position, _targetResource.transform.position) > 1.8f)
            {
                _state = HelperState.SeekingResource;
            }
        }

        private void UpdateReturning()
        {
            if (furnace == null)
            {
                _state = HelperState.SeekingResource;
                return;
            }

            _agent.SetDestination(furnace.transform.position);
            if (_inventory.Total == 0 && !_agent.pathPending && _agent.remainingDistance <= 2f)
            {
                _agent.isStopped = true;
                _workUntil = Time.time + workPause;
                _state = HelperState.WorkingAtFurnace;
            }
        }

        private void UpdateWorkingAtFurnace()
        {
            if (Time.time < _workUntil)
            {
                return;
            }

            _agent.isStopped = false;
            _targetResource = null;
            _state = HelperState.SeekingResource;
        }

        private void BeginReturn()
        {
            _targetResource = null;
            _state = HelperState.ReturningToFurnace;
        }

        private HavenlineResourceNode FindBestResource()
        {
            return FindObjectsByType<HavenlineResourceNode>(FindObjectsSortMode.None)
                .Where(node => !node.IsDepleted)
                .OrderBy(node => (node.transform.position - transform.position).sqrMagnitude)
                .FirstOrDefault();
        }

        private void UpdateAnimation()
        {
            if (animator == null)
            {
                return;
            }

            animator.SetFloat(_speedHash, _agent.velocity.magnitude, 0.08f, Time.deltaTime);
            animator.SetBool(
                _workHash,
                _state is HelperState.Gathering or HelperState.WorkingAtFurnace);
        }
    }
}
