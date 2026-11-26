const fs = require('fs');
const path = require('path');
const { chrome } = require('jest-chrome');

describe('finance_hub_core.js', () => {
  beforeEach(() => {
    // Reset DOM
    document.body.innerHTML = '';
    
    // Reset global variables
    window.financeHubParser = undefined;
    window.financeHubUI = undefined;
    
    // Mock localStorage
    const localStorageMock = (function() {
      let store = {};
      return {
        getItem: jest.fn(key => store[key] || null),
        setItem: jest.fn((key, value) => {
          store[key] = value.toString();
        }),
        clear: jest.fn(() => {
          store = {};
        })
      };
    })();
    Object.defineProperty(window, 'localStorage', { value: localStorageMock });
    
    // Clear chrome mock
    chrome.runtime.sendMessage.mockClear();
    
    // Mock chrome.storage.sync.get
    chrome.storage = {
      sync: {
        get: jest.fn((keys, callback) => callback({ backendBaseUrl: 'http://localhost:8080' }))
      }
    };
    
    // Mock fetch
    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ _mockBackend: true })
      })
    );
  });

  const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0));

  const loadCoreScript = () => {
    jest.resetModules();
    require('../../core/finance_hub_core.js');
  };

  it('injects the trigger button into the DOM', () => {
    loadCoreScript();
    
    const btn = document.getElementById('finance-hub-trigger-btn');
    expect(btn).not.toBeNull();
    expect(btn.textContent).toContain('Preview & Send to Finance Hub');
  });

  it('triggers parsing and UI preview on click', async () => {
    // Setup mocks
    window.financeHubParser = {
      platform: 'rakuten',
      parse: jest.fn().mockReturnValue({ orderId: '123' })
    };
    
    window.financeHubUI = {
      showPreviewPanel: jest.fn()
    };

    chrome.runtime.sendMessage.mockImplementation((msg, callback) => {
      callback({ installed: true });
    });

    loadCoreScript();

    const btn = document.getElementById('finance-hub-trigger-btn');
    btn.click(); // click now triggers async code
    
    await flushPromises();

    expect(window.financeHubParser.parse).toHaveBeenCalled();
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith({ action: 'pingNativeHost' }, expect.any(Function));
    expect(window.financeHubUI.showPreviewPanel).toHaveBeenCalledWith({ orderId: '123' }, true, 'rakuten', { _mockBackend: true });
    expect(localStorage.setItem).toHaveBeenCalledWith('financeHubPanelOpen', 'true');
  });

  it('handles button dragging', () => {
    loadCoreScript();
    const btn = document.getElementById('finance-hub-trigger-btn');
    btn.getBoundingClientRect = jest.fn().mockReturnValue({ left: 10, top: 10, width: 100, height: 50 });
    Object.defineProperty(btn, 'offsetWidth', { value: 100 });
    Object.defineProperty(btn, 'offsetHeight', { value: 50 });
    window.innerWidth = 1000;
    window.innerHeight = 800;

    // mousedown
    const mousedownEvent = new MouseEvent('mousedown', { clientX: 20, clientY: 20 });
    btn.dispatchEvent(mousedownEvent);

    // mousemove
    const mousemoveEvent = new MouseEvent('mousemove', { clientX: 100, clientY: 100 });
    document.dispatchEvent(mousemoveEvent);

    expect(btn.style.left).toBe('90px');
    expect(btn.style.top).toBe('90px');

    // mouseup
    const mouseupEvent = new MouseEvent('mouseup');
    document.dispatchEvent(mouseupEvent);

    expect(localStorage.setItem).toHaveBeenCalledWith('financeHubTriggerBtnPos', expect.any(String));

    // click after drag should be ignored
    const clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true });
    btn.dispatchEvent(clickEvent);
    expect(clickEvent.defaultPrevented).toBe(true);
  });

  it('restores button position from localStorage', () => {
    localStorage.getItem.mockImplementation(key => {
      if (key === 'financeHubTriggerBtnPos') return JSON.stringify({ left: 200, top: 300 });
      return null;
    });
    loadCoreScript();
    
    const btn = document.getElementById('finance-hub-trigger-btn');
    expect(btn.style.left).toBe('200px');
    expect(btn.style.top).toBe('300px');
  });

  it('auto-opens panel if localStorage financeHubPanelOpen is true', async () => {
    localStorage.getItem.mockImplementation(key => {
      if (key === 'financeHubPanelOpen') return 'true';
      return null;
    });
    
    window.financeHubParser = { parse: jest.fn(), platform: 'amazon' };
    window.financeHubUI = { showPreviewPanel: jest.fn() };
    chrome.runtime.sendMessage.mockImplementation((msg, callback) => callback({ installed: true }));

    loadCoreScript();
    
    await flushPromises();

    expect(window.financeHubParser.parse).toHaveBeenCalled();
    expect(window.financeHubUI.showPreviewPanel).toHaveBeenCalled();
  });

  it('shows alert when parsing fails (no parser)', () => {
    window.alert = jest.fn();
    console.error = jest.fn();
    loadCoreScript();

    const btn = document.getElementById('finance-hub-trigger-btn');
    btn.click();

    expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('Parsing failed: No parser found'));
  });

  it('closes panel if already exists on click', () => {
    loadCoreScript();
    const btn = document.getElementById('finance-hub-trigger-btn');
    
    const panel = document.createElement('div');
    panel.id = 'finance-preview-panel';
    document.body.appendChild(panel);

    btn.click();
    
    expect(document.getElementById('finance-preview-panel')).toBeNull();
    expect(localStorage.setItem).toHaveBeenCalledWith('financeHubPanelOpen', 'false');
  });

  it('does not inject a second button if one already exists', () => {
    loadCoreScript();
    const btn1 = document.getElementById('finance-hub-trigger-btn');
    
    // Call again
    loadCoreScript();
    
    const btns = document.querySelectorAll('#finance-hub-trigger-btn');
    expect(btns.length).toBe(1);
    expect(btns[0]).toBe(btn1);
  });

  it('logs error if financeHubUI is missing on click', async () => {
    console.error = jest.fn();
    window.financeHubParser = { parse: jest.fn(), platform: 'amazon' };
    window.financeHubUI = undefined; // Missing UI
    chrome.runtime.sendMessage.mockImplementation((msg, callback) => callback({ installed: true }));

    loadCoreScript();
    
    const btn = document.getElementById('finance-hub-trigger-btn');
    btn.click();
    
    await flushPromises();
    
    expect(console.error).toHaveBeenCalledWith("Finance Hub UI not loaded.");
  });

  it('handles mouseup without dragging (normal click)', () => {
    loadCoreScript();
    const btn = document.getElementById('finance-hub-trigger-btn');
    
    // mousedown
    const mousedownEvent = new MouseEvent('mousedown', { clientX: 20, clientY: 20 });
    btn.dispatchEvent(mousedownEvent);
    
    // mouseup immediately (no mousemove)
    const mouseupEvent = new MouseEvent('mouseup');
    document.dispatchEvent(mouseupEvent);
    
    // isDragged was false, so setItem should NOT have been called
    expect(localStorage.setItem).not.toHaveBeenCalledWith('financeHubTriggerBtnPos', expect.any(String));
  });
});
