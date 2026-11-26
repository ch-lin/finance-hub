const fs = require('fs');
const path = require('path');
const { chrome } = require('jest-chrome');

describe('popup.js', () => {
  beforeEach(() => {
    jest.useFakeTimers();

    // Reset DOM
    document.body.innerHTML = `
      <input type="text" id="backendBaseUrl" />
      <input type="checkbox" id="dryRun" />
      <button id="saveBtn">Save</button>
      <div id="status" style="display: none;">Saved</div>
    `;

    // Reset chrome mock
    chrome.storage.sync.get.mockClear();
    chrome.storage.sync.set.mockClear();

    // Default mock implementation
    chrome.storage.sync.get.mockImplementation((keys, callback) => {
      callback({ backendBaseUrl: 'http://test.url', dryRun: true });
    });

    // We must load the script using require so Jest can collect coverage.
    jest.resetModules();
    require('../popup.js');
  });

  it('loads settings on DOMContentLoaded', () => {
    // Trigger DOMContentLoaded
    document.dispatchEvent(new Event('DOMContentLoaded'));

    expect(chrome.storage.sync.get).toHaveBeenCalledWith(['backendBaseUrl', 'dryRun'], expect.any(Function));
    
    expect(document.getElementById('backendBaseUrl').value).toBe('http://test.url');
    expect(document.getElementById('dryRun').checked).toBe(true);
  });

  it('saves settings when save button is clicked', () => {
    // Trigger DOMContentLoaded to attach listeners
    document.dispatchEvent(new Event('DOMContentLoaded'));

    // Mock the set function callback
    chrome.storage.sync.set.mockImplementation((data, callback) => {
      callback();
    });

    // Change values
    document.getElementById('backendBaseUrl').value = 'http://new.url';
    document.getElementById('dryRun').checked = false;

    // Click save
    document.getElementById('saveBtn').click();

    expect(chrome.storage.sync.set).toHaveBeenCalledWith({ backendBaseUrl: 'http://new.url', dryRun: false }, expect.any(Function));
    
    // Status should be visible temporarily
    expect(document.getElementById('status').style.display).toBe('block');

    // Fast-forward time to check if status disappears
    jest.advanceTimersByTime(2000);
    expect(document.getElementById('status').style.display).toBe('none');
  });

  it('falls back to default Base URL if input is empty', () => {
    document.dispatchEvent(new Event('DOMContentLoaded'));
    
    chrome.storage.sync.set.mockImplementation((data, callback) => {
      callback();
    });

    document.getElementById('backendBaseUrl').value = '   '; // Mock empty input
    document.getElementById('saveBtn').click();

    expect(chrome.storage.sync.set).toHaveBeenCalledWith(
      { backendBaseUrl: 'http://localhost:8080', dryRun: true },
      expect.any(Function)
    );
  });
});
