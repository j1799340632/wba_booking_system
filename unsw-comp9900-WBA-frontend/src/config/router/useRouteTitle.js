import { useEffect } from 'react';
import { useMatches } from 'react-router-dom';

/**
 * Listen to route changes and update the document title accordingly.
 */
export function useRouteTitle(defaultTitle = 'WBA-Platform') {
  const matches = useMatches();

  useEffect(() => {
    // Get current route
    const currentMatch = matches[matches.length - 1];
    const handle = currentMatch?.handle;

    if (handle?.title) {
      document.title = handle.title;
    } else {
      document.title = defaultTitle;
    }
  }, [matches, defaultTitle]);
}
