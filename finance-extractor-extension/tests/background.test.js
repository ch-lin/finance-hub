const fs = require('fs');
const path = require('path');
const { chrome } = require('jest-chrome');

describe('background.js', () => {
  beforeEach(() => {
    // Reset chrome mock
    chrome.runtime.onMessage.clearListeners();
    jest.spyOn(chrome.runtime.onMessage, 'addListener').mockClear();
    chrome.runtime.sendNativeMessage.mockClear();

    // Load background script using require to allow coverage instrumentation
    jest.resetModules();
    require('../background.js');
  });

  it('handles pingNativeHost message', () => {
    // Call the registered listener
    const listener = chrome.runtime.onMessage.addListener.mock.calls[0][0];
    const sendResponseMock = jest.fn();

    // Trigger ping
    const isAsync = listener({ action: 'pingNativeHost' }, {}, sendResponseMock);
    
    // Should return true to keep channel open
    expect(isAsync).toBe(true);
    
    expect(chrome.runtime.sendNativeMessage).toHaveBeenCalledWith(
      'ch.lin.finance.hub.native', 
      { command: 'ping' }, 
      expect.any(Function)
    );

    // Simulate native message response success
    const sendNativeMessageCallback = chrome.runtime.sendNativeMessage.mock.calls[0][2];
    sendNativeMessageCallback({ status: 'ok' });

    expect(sendResponseMock).toHaveBeenCalledWith({ installed: true });
  });

  it('handles triggerNativeSave message', () => {
    const listener = chrome.runtime.onMessage.addListener.mock.calls[0][0];
    const sendResponseMock = jest.fn();

    const isAsync = listener({ action: 'triggerNativeSave' }, {}, sendResponseMock);
    
    expect(isAsync).toBe(true);
    expect(chrome.runtime.sendNativeMessage).toHaveBeenCalledWith(
      'ch.lin.finance.hub.native', 
      { command: 'save_page' }, 
      expect.any(Function)
    );

    // Simulate failure
    chrome.runtime.lastError = { message: 'Host not found' };
    const sendNativeMessageCallback = chrome.runtime.sendNativeMessage.mock.calls[0][2];
    sendNativeMessageCallback(undefined);

    expect(sendResponseMock).toHaveBeenCalledWith({ success: false, error: 'Host not found' });
  });
});
