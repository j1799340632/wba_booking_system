import {
  LayoutDashboard,
  CalendarDays,
  ClipboardList,
  History,
  CircleUserRound,
  HospitalIcon,
  Type,
  Users2,
  UserStar,
  LucideWarehouse
} from "lucide-react";
import DashboardPageAdmin from "@/pages/Main/Home/Admin/Dashboard/index.jsx";
import AssistantInsightsPage from "@/pages/Main/Home/Admin/AssistantInsights/index.jsx";
import DashboardPageStudent from "@/pages/Main/Home/Student/Dashboard/index.jsx";
import DashboardPageSupervisor from "@/pages/Main/Home/Supervisor/Dashboard/index.jsx";
import BookSlotStudent from "@/pages/Main/Home/Student/BookSlot/index.jsx";
import MyBookingsStudent from "@/pages/Main/Home/Student/MyBooking/index.jsx";
import AssessmentHistoryStudent from "@/pages/Main/Home/Student/AssessmentHistory/index.jsx";
import AssessmentPlanPage from "@/pages/Main/Home/Student/AssessmentPlan/index.jsx";
import SlotsSupervisor from "@/pages/Main/Home/Supervisor/Slot/index.jsx";
import UpcomingSessionSupervisor from "@/pages/Main/Home/Supervisor/UpcomingSession/index.jsx";
import AssessmentHistorySupervisor from "@/pages/Main/Home/Supervisor/AssessmentHistory/index.jsx";
import Profile from "@/pages/Main/Home/Supervisor/Profile/index.jsx";
import AllBookingsAdmin from "@/pages/Main/Home/Admin/AllBookings/index.jsx";
import StudentProfile from "@/pages/Main/Home/Student/StudentProfile/index.jsx"
import UserManage from "@/pages/Main/Home/Admin/UserManage/index.jsx";
import Settings from "@/pages/Main/Home/Settings/index.jsx";
import HospitalManage from "@/pages/Main/Home/Admin/HospitalManage/index.jsx";
import DepartmentManage from "@/pages/Main/Home/Admin/DepartmentManage/index.jsx";
import WbaTypeManage from "@/pages/Main/Home/Admin/WbaTypeManage/index.jsx";
import RoleManage from "@/pages/Main/Home/SuperAdmin/UserRoleManage/index.jsx";
import AdminProfile from "@/pages/Main/Home/Admin/Profile/index.jsx";
import SuperAdminProfile from "@/pages/Main/Home/SuperAdmin/Profile/index.jsx";

const SUPER_ADMIN_ROLE_ID = "1"
const ADMIN_ROLE_ID = "2"
const SUPERVISOR_ROLE_ID = "3"
const STUDENT_ROLE_ID = "4"

export const COMMON_ROUTES = [
  {
    path: '/home/settings',
    element: <Settings />,
    roles: [ADMIN_ROLE_ID, SUPERVISOR_ROLE_ID, STUDENT_ROLE_ID],
    handle: {
      title: 'Settings',
    },
    showInMenu: false,
  },
]

export const SUPER_ADMIN_ROUTES = [
  {
    path: "/home/super-admin/dashboard",
    element: <DashboardPageAdmin />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: "Dashboard",
      icon: LayoutDashboard,
    },
    showInMenu: true,
  },
  {
    path: "/home/super-admin/assistant-insights",
    element: <AssistantInsightsPage />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: { title: "AI Insights", icon: ClipboardList },
    showInMenu: true,
  },
  {
    path: "/home/super-admin/user-role-manage",
    element: <RoleManage />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: "Role Manage",
      icon: UserStar,
    },
    showInMenu: true,
  },
  {
    path: "/home/super-admin/user-manage",
    element: <UserManage />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: "User Manage",
      icon: Users2,
    },
    showInMenu: true,
  },
  {
    path: "/home/super-admin/hospital-manage",
    element: <HospitalManage />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: "Hospital Manage",
      icon: HospitalIcon,
    },
    showInMenu: true,
  },
  {
    path: "/home/super-admin/department-manage",
    element: <DepartmentManage />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: "Department Manage",
      icon: LucideWarehouse,
    },
    showInMenu: true,
  },
  {
    path: "/home/super-admin/wba-type-manage",
    element: <WbaTypeManage />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: "Wba Type Manage",
      icon: Type,
    },
    showInMenu: true,
  },
  {
    path: '/home/super-admin/profile',
    element: <SuperAdminProfile />,
    roles: [SUPER_ADMIN_ROLE_ID],
    handle: {
      title: 'Profile',
      icon: CircleUserRound
    },
    showInMenu: true,
  },
]

export const ADMIN_ROUTES = [
  {
    path: "/home/admin/dashboard",
    element: <DashboardPageAdmin />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: "Dashboard",
      icon: LayoutDashboard,
    },
    showInMenu: true,
  },
  {
    path: "/home/admin/assistant-insights",
    element: <AssistantInsightsPage />,
    roles: [ADMIN_ROLE_ID],
    handle: { title: "AI Insights", icon: ClipboardList },
    showInMenu: true,
  },
  {
    path: "/home/admin/all-booking",
    element: <AllBookingsAdmin />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: "All Bookings",
      icon: ClipboardList,
    },
    showInMenu: true,
  },
  {
    path: "/home/admin/user-manage",
    element: <UserManage />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: "User Manage",
      icon: Users2,
    },
    showInMenu: true,
  },
  {
    path: "/home/admin/hospital-manage",
    element: <HospitalManage />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: "Hospital Manage",
      icon: HospitalIcon,
    },
    showInMenu: true,
  },
  {
    path: "/home/admin/department-manage",
    element: <DepartmentManage />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: "Department Manage",
      icon: LucideWarehouse,
    },
    showInMenu: true,
  },
  {
    path: "/home/admin/wba-type-manage",
    element: <WbaTypeManage />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: "Wba Type Manage",
      icon: Type,
    },
    showInMenu: true,
  },
  {
    path: '/home/admin/profile',
    element: <AdminProfile />,
    roles: [ADMIN_ROLE_ID],
    handle: {
      title: 'Profile',
      icon: CircleUserRound
    },
    showInMenu: true,
  },
];

export const SUPERVISOR_ROUTES = [
  {
    path: '/home/supervisor/dashboard',
    element: <DashboardPageSupervisor />,
    roles: [SUPERVISOR_ROLE_ID],
    handle: {
      title: 'Dashboard',
      icon: LayoutDashboard,
    },
    showInMenu: true,
  },
  {
    path: '/home/supervisor/availability',
    element: <SlotsSupervisor />,
    roles: [SUPERVISOR_ROLE_ID],
    handle: {
      title: 'Slot',
      icon: CalendarDays
    },
    showInMenu: true,
  },
  {
    path: '/home/supervisor/upcoming-sessions',
    element: <UpcomingSessionSupervisor />,
    roles: [SUPERVISOR_ROLE_ID],
    handle: {
      title: 'Upcoming Sessions',
      icon: ClipboardList
    },
    showInMenu: true,
  },
  {
    path: '/home/supervisor/assessment-history',
    element: <AssessmentHistorySupervisor />,
    roles: [SUPERVISOR_ROLE_ID],
    handle: {
      title: 'Assessment History',
      icon: History
    },
    showInMenu: true,
  },
  {
    path: "/home/supervisor/student-profile",
    element: <StudentProfile />,
    roles: [SUPERVISOR_ROLE_ID],
    handle: {
      title: "Student Profile",
      icon: CircleUserRound,
    },
    showInMenu: false,
  },
  {
    path: '/home/supervisor/profile',
    element: <Profile />,
    roles: [SUPERVISOR_ROLE_ID],
    handle: {
      title: 'Profile',
      icon: CircleUserRound
    },
    showInMenu: true,
  },
]

export const STUDENT_ROUTES = [
  {
    path: '/home/student/assessment-plan',
    element: <AssessmentPlanPage />,
    roles: [STUDENT_ROLE_ID],
    handle: { title: 'Assessment Plan', icon: ClipboardList },
    showInMenu: true,
  },
  {
    path: '/home/student/dashboard',
    element: <DashboardPageStudent />,
    roles: [STUDENT_ROLE_ID],
    handle: {
      title: 'Dashboard',
      icon: LayoutDashboard,
    },
    showInMenu: true,
  },
  {
    path: '/home/student/bookSlot',
    element: <BookSlotStudent />,
    roles: [STUDENT_ROLE_ID],
    handle: {
      title: 'Book Slots',
      icon: CalendarDays
    },
    showInMenu: true,
  },
  {
    path: '/home/student/my-bookings',
    element: <MyBookingsStudent />,
    roles: [STUDENT_ROLE_ID],
    handle: {
      title: 'My Bookings',
      icon: ClipboardList
    },
    showInMenu: true,
  },
  {
    path: '/home/student/assessment-history',
    element: <AssessmentHistoryStudent />,
    roles: [STUDENT_ROLE_ID],
    handle: {
      title: 'Assessment History',
      icon: History
    },
    showInMenu: true,
  },
  {
    path: '/home/student/supervisor-profile',
    element: <Profile />,
    roles: [STUDENT_ROLE_ID],
    handle: {
      title: 'SupervisorProfile',
      icon: CircleUserRound
    },
    showInMenu: false,
  },
  {
    path: '/home/student/profile',
    element: <StudentProfile />,
    roles: [STUDENT_ROLE_ID],
    handle: {
      title: 'Profile',
      icon: CircleUserRound
    },
    showInMenu: true,
  },
]

export const ROUTE_MAP = {
  [SUPER_ADMIN_ROLE_ID]: SUPER_ADMIN_ROUTES,
  [ADMIN_ROLE_ID]: ADMIN_ROUTES,
  [SUPERVISOR_ROLE_ID]: SUPERVISOR_ROUTES,
  [STUDENT_ROLE_ID]: STUDENT_ROUTES,
}
