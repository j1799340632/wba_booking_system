/**
 * Gernate menu
 * @param {Array} routes routes array
 */
export function generateMenu(routes) {
    if (!routes || routes.length === 0) return [];
    return routes
        .filter(route => {
            return route.showInMenu
        })
        .map(route => {
            // Recursive generation of children
            if (route.children && route.children.length > 0) {
                return {
                    ...route,
                    children: generateMenu(route.children)
                };
            }
            return route;
        });
}

/**
 * Match routes by path
 * @param {Array} routes routes array
 * @param {string} targetPath target path
 * @returns {Object|null} route object
 */
export const findRouteByPath = (routes, targetPath) => {
  for (const route of routes) {
    // Match by path
    if (route.path === targetPath) {
      return route;
    }

    // Recursive search for children
    if (route.children && route.children.length > 0) {
      const found = findRouteByPath(route.children, targetPath);
      if (found) return found;
    }
  }
  return [];
}
