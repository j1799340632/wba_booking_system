import "./index.css";
import {forwardRef, useEffect, useImperativeHandle, useState} from "react";
import { Bell, CalendarDays, X } from "lucide-react";
import { useSse } from "@/utils/sse/useSse.js";
import { notificationEvent } from "@/utils/sse/sseEventChannel.js";
import { getMessageList, readMessages } from "@/config/request/api/messageApi.js";

const NotificationBell = forwardRef((props, ref) => {
  const [notifications, setNotifications] = useState([]);
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [highlightIds, setHighlightIds] = useState([]);

  const normalizeMessage = (m) => ({
    id: m.id,
    eventName: m.creatorName ? `From ${m.creatorName}` : "Notification",
    eventContent: m.content,
    createdAt: m.createTime,
    isRead: m.isRead,
  });

  const isUnreadSummary = (content) =>
    /received\s+\d+\s+messages.*check the message center/i.test(content || "");

  const loadNotifications = () => {
    getMessageList()
      .then((res) => {
        const list = (res?.data || [])
          .filter((m) => !isUnreadSummary(m.content))
          .map(normalizeMessage)
          .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        setNotifications(list);
        setUnreadCount(list.filter((m) => !m.isRead).length);
      })
      .catch((err) => console.log(err));
  };

  useEffect(() => {
    loadNotifications();
  }, []);

  useSse(notificationEvent, () => {
    loadNotifications();
  });

  const handleOpen = e => {
    if (e && e.stopPropagation) {
      e.stopPropagation();
    }

    setOpen(true);

    const unreadIds = notifications.filter((m) => !m.isRead).map((m) => m.id);

    setHighlightIds(unreadIds);

    if (unreadIds.length > 0) {
      readMessages(unreadIds)
        .then(() => {
          setNotifications((prev) => prev.map((m) => ({ ...m, isRead: true })));
          setUnreadCount(0);
        })
        .catch((err) => console.log(err));
    } else {
      setUnreadCount(0);
    }
  };

  const handleClose = (e) => {
    if (e && e.stopPropagation) {
      e.stopPropagation();
    }
    setOpen(false);
  };

  useImperativeHandle(ref, () => ({
    open: () => {
      handleOpen();
    }
  }));

  return (
    <>
      <button
        type="button"
        className="notification-bell-button"
        onClick={handleOpen}
      >
        <Bell size={18} style={{color: '#4b5563'}} />

        {unreadCount > 0 && (
          <span className="notification-bell-badge">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <button
          type="button"
          className="notification-bell-overlay"
          onClick={() => setOpen(false)}
        />
      )}

      <aside
        className={`notification-bell-drawer ${open ? "is-open" : ""}`}
      >
        <div className="notification-bell-header">
          <div>
            <h2 className="notification-bell-heading">Notifications</h2>

            <p className="notification-bell-summary">Notification</p>
          </div>

          <button
            type="button"
            className="notification-bell-close"
            onClick={handleClose}
          >
            <X size={22} />
          </button>
        </div>

        <div className="notification-bell-list">
          {notifications.length === 0 ? (
            <div className="notification-bell-empty">
              <Bell size={28} />

              <p>No notifications yet.</p>

              <span>New updates will appear here.</span>
            </div>
          ) : (
            notifications.map((notification) => {
              const isNew =
                highlightIds.includes(notification.id) || !notification.isRead;

              return (
                <div
                  key={notification.id}
                  className={`notification-bell-item ${
                    isNew ? "is-unread" : ""
                  }`}
                >
                  <div className="notification-bell-icon">
                    <CalendarDays size={21} />
                  </div>

                  <div className="notification-bell-content">
                    <h3>{notification.eventName}</h3>

                    <p>{notification.eventContent}</p>

                    <span>
                      {formatNotificationTime(notification.createdAt)}
                    </span>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </aside>
    </>
  );
})

export default NotificationBell;

function formatNotificationTime(value) {
  if (!value) return "";

  const normalized =
    typeof value === "string" ? value.replace(" ", "T") : value;
  const d = new Date(normalized);

  if (Number.isNaN(d.getTime())) return "";

  return d.toLocaleString("en-AU", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}
