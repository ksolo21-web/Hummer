const carry = document.querySelector('#carry');
const carryLock = document.querySelector('#carryLock');

if (carryLock) carryLock.textContent = 'Unlimited';

function refreshCarryLabel() {
  if (carry) {
    const match = carry.textContent.match(/^\s*(\d+)/);
    const count = match ? match[1] : '0';
    carry.textContent = `${count} carried`;
  }
  if (carryLock && carryLock.textContent !== 'Unlimited') carryLock.textContent = 'Unlimited';
  requestAnimationFrame(refreshCarryLabel);
}

requestAnimationFrame(refreshCarryLabel);
