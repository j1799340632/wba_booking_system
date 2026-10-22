import { createRoot } from 'react-dom/client';
import LoadingUI from './LoadingUI';

let loadingContainer = null;
let root = null;
let targetElement = null;
let originalPosition = '';
let originalOverflow = '';

export const Loading = {
  show(options = {}) {
    if (loadingContainer) return;

    const { text = 'Loading...', targetId } = options;
    targetElement = targetId ? document.getElementById(targetId) : document.getElementById('home-container');

    if (!targetElement) {
      targetElement = document.body;
    }

    let dynamicStyle;

    // If it's a partial mount, the offset is calculated dynamically
    if (targetElement !== document.body) {
      const style = window.getComputedStyle(targetElement);
      originalPosition = targetElement.style.position;
      if (style.position === 'static') {
        targetElement.style.position = 'relative';
      }

      // Read the current scroll position and view size
      dynamicStyle = {
        top: `${targetElement.scrollTop}px`,
        left: `${targetElement.scrollLeft}px`,
        width: `${targetElement.clientWidth}px`,
        height: `${targetElement.clientHeight}px`,
      };
    } else {
      // Fullscreen
      dynamicStyle = {
        position: 'fixed',
        inset: '0'
      };
    }

    originalOverflow = targetElement.style.overflow;
    targetElement.style.overflow = 'hidden';

    loadingContainer = document.createElement('div');
    targetElement.appendChild(loadingContainer);

    root = createRoot(loadingContainer);

    root.render(<LoadingUI text={text} dynamicStyle={dynamicStyle} />);
  },

  hide() {
    if (!loadingContainer) return;

    if (root) {
      root.unmount();
      root = null;
    }

    if (loadingContainer.parentNode) {
      loadingContainer.parentNode.removeChild(loadingContainer);
    }
    loadingContainer = null;

    if (targetElement) {
      if (targetElement !== document.body) {
        targetElement.style.position = originalPosition;
      }
      targetElement.style.overflow = originalOverflow;
      targetElement = null;
    }
  }
};
