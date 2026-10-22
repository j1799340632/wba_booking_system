import {createBrowserRouter} from 'react-router-dom';
import App from '@/App.jsx';
import Welcome from "@/pages/Main/Welcome";
import Login from "@/pages/Main/Welcome/Login";
import Home from "@/pages/Main/Home/";
import {
  STUDENT_ROUTES,
  SUPERVISOR_ROUTES,
  ADMIN_ROUTES,
  SUPER_ADMIN_ROUTES,
  COMMON_ROUTES
} from "@/config/router/routeMap.jsx";
import {requireAuth} from "@/config/router/authLoader.jsx";

const autoInjectLoaders = (routes) => {
  return routes.map(route => {
    const newRoute = { ...route };

    // Bind loader function if roles are specified
    if (route.roles) {
      newRoute.loader = requireAuth(route.roles);
    }

    // Recursively inject loaders for children's routes
    if (route.children) {
      newRoute.children = autoInjectLoaders(route.children);
    }

    return newRoute;
  });
};

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        path: '',
        element: <Welcome />,
        handle: { title: 'Presto-Platform' },
        children: [
          {
            path: '',
            element: <Login />,
            handle: { title: 'WBA-Platform' },
          },
        ],
      },
      {
        path: '/home',
        element: <Home />,
        handle: { title: 'WBA-Home' },
        children: [
          ...autoInjectLoaders(COMMON_ROUTES),
          ...autoInjectLoaders(SUPER_ADMIN_ROUTES),
          ...autoInjectLoaders(ADMIN_ROUTES),
          ...autoInjectLoaders(SUPERVISOR_ROUTES),
          ...autoInjectLoaders(STUDENT_ROUTES),
        ],
      },
    ],
  },
  {
    path: '*',
    element: <div style={{textAlign: 'center'}}><h1>404 Not Found</h1></div>,
    handle: { title: 'WBA-404 Not Found' }
  },
  {
    path: '*',
    element: <div style={{textAlign: 'center'}}><h1>403 No Permission</h1></div>,
    handle: { title: 'WBA-403 No Permission' }
  },
]);

export default router;
