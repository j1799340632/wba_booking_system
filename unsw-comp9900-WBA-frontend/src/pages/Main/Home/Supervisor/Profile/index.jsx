import "./index.css";
import { useEffect, useState } from "react";
import {
  ArrowLeft,
  Building2,
  Clock,
  Edit3,
  Mail,
  MapPin,
  Stethoscope,
} from "lucide-react";
import { getProfile, updateProfile, uploadFile } from "@/config/request/api/userApi.js";
import { notify } from "@/utils/notify.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import { useUserStore } from "@/config/store/useUserStore.js";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAvailabilityStore } from "@/config/store/useAvailabilityStore.js";
import {EMAIL_REGEX} from "@/config/constant/RegexPatternConstant.js";

const dayOptions = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];

const timeOptions = [
  "07:00", "07:30", "08:00", "08:30", "09:00", "09:30",
  "10:00", "10:30", "11:00", "11:30", "12:00", "12:30",
  "13:00", "13:30", "14:00", "14:30", "15:00", "15:30",
  "16:00", "16:30", "17:00",
];

function userToProfile(user) {
  if (!user) return null;

  return {
    name: user.name || user.username || user.fullName || "Supervisor",
    title: user.title || "",
    department: user.department || "",
    email: user.email || "",
    hospital: user.hospital || "",
    location: user.location || "",
    availableDays: user.availableDays
      ? user.availableDays.split(",").map((d) => d.trim()).filter(Boolean)
      : [],
    startTime: user.availableStartTime
      ? user.availableStartTime.slice(0, 5)
      : "09:00",
    endTime: user.availableEndTime
      ? user.availableEndTime.slice(0, 5)
      : "15:00",
    studentLevel: user.preferredStudentLevel || "",
    wbaTypes: user.preferredAssessType || "",
    avatar: user.avatar || "",
  };
}

export default function Profile() {

    const navigate = useNavigate();

    const [searchParams] = useSearchParams();
    const supervisorId = searchParams.get("id");
    const viewingSupervisor = Boolean(supervisorId);
  const savedUser = useUserStore((state) => state.userInfo);
  const isAvailable = useAvailabilityStore((state) => state.isAvailable);
  const initialProfile = userToProfile(savedUser);

  const [profile, setProfile] = useState(initialProfile);
  const [form, setForm] = useState(initialProfile);

  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [avatarFile, setAvatarFile] = useState(null);

  const user = useUserStore((state) => state.userInfo);
  const setUserInfo = useUserStore((state) => state.setUserInfo);

  useEffect(() => {
    getProfile(supervisorId ? supervisorId : "").then((data) => {
      const profileData = data.data;
      const userProfile = userToProfile(profileData);

      setProfile(userProfile);
      setForm(userProfile);

      if (!supervisorId) {
        const currentUser = useUserStore.getState().userInfo;

        setUserInfo({
          ...currentUser,
          avatar: profileData.avatar || "",
          avatarUpdatedAt: Date.now(),
        });
      }
    });
  }, []);

  const openEdit = () => {
    setForm(profile);
    setAvatarFile(null);
    setMessage("");
    setEditing(true);
  };

  const startUpdateProfile = (nextProfile, avatarUrl) => {
    const payload = {
      title: nextProfile.title,
      location: nextProfile.location,
      hospital: nextProfile.hospital,
      department: nextProfile.department,
      preferredAssessType: nextProfile.wbaTypes,
      preferredStudentLevel: nextProfile.studentLevel,
      availableDays: nextProfile.availableDays.join(","),
      availableStartTime: `${nextProfile.startTime}:00`,
      availableEndTime: `${nextProfile.endTime}:00`,
      avatar: avatarUrl || "",
      roleId: user.roleId,
    };

    return updateProfile(payload).then((data) => data.data);
  };

  const checkEmail = (email) => {
    if (!email || !EMAIL_REGEX.test(email)) {
      notify.warning("Please enter a valid email address")
      return false
    }
    return true
  }

  const saveProfile = async () => {
    setSaving(true);
    setMessage("");

    try {
      if (!checkEmail(form.email)) {
        return
      }

      let avatarUrl = profile.avatar || "";

      if (avatarFile) {
        const formData = new FormData();
        formData.append("file", avatarFile);
        avatarUrl = (await uploadFile(formData)).data;
      }

      startUpdateProfile(form, avatarUrl).then(() => {
        const updatedProfile = {
          ...form,
          avatar: avatarUrl,
        };

        setProfile(updatedProfile);

        const currentUser = useUserStore.getState().userInfo;

          setUserInfo({
            ...currentUser,
            title: form.title,
            location: form.location,
            hospital: form.hospital,
            department: form.department,
            preferredAssessType: form.wbaTypes,
            preferredStudentLevel: form.studentLevel,
            availableDays: form.availableDays.join(","),
            availableStartTime: `${form.startTime}:00`,
            availableEndTime: `${form.endTime}:00`,
            avatar: avatarUrl,
            avatarUpdatedAt: Date.now(),
          });

        window.dispatchEvent(new Event("userUpdated"));

        setEditing(false);
        setAvatarFile(null);
        notify.success("Profile updated successfully.");
        setMessage("Profile updated successfully.");
      });
    } finally {
      setSaving(false);
    }
  };

  const toggleDay = (day) => {
    setForm((prev) => ({
      ...prev,
      availableDays: prev.availableDays.includes(day)
        ? prev.availableDays.filter((d) => d !== day)
        : [...prev.availableDays, day],
    }));
  };

  const getInitials = (name) => {
    return name
      .split(" ")
      .filter(Boolean)
      .map((word) => word[0])
      .join("")
      .slice(0, 2)
      .toUpperCase();
  };
  return (
    <div>
      <main className="profile-main">
      <div className="profile-header">
          <div>
            <h1 className="profile-title">
              {viewingSupervisor ? "Supervisor Profile" : "My Profile"}
            </h1>

            <p className="profile-subtitle">
              {viewingSupervisor
                ? "View the supervisor's professional details and availability"
                : "Manage your professional details and teaching preferences"}
            </p>
          </div>

          {viewingSupervisor ? (
            <button
              type="button"
              className="profile-edit-button"
              onClick={() => navigate("/home/student/bookSlot")}
            >
              <ArrowLeft size={15} />
              Back to Book Slots
            </button>
          ) : (
            <button
              type="button"
              className="profile-edit-button"
              onClick={openEdit}
            >
              <Edit3 size={15} />
              Edit Profile
            </button>
          )}
        </div>

        {message && <div className="profile-message">{message}</div>}

        <section className="profile-card">
          <div className="profile-avatar">
            {profile.avatar ? (
              <img
                src={profile.avatar}
                alt="avatar"
                className="profile-avatar-image"
              />
            ) : (
              getInitials(profile.name)
            )}
          </div>

          <div className="profile-main-info">
            <h2 className="profile-name">{profile.name}</h2>

            <p className="profile-role">
              {profile.title || "No title"} ·{" "}
              {profile.department || "No department"}
            </p>

            <div className="profile-info-row">
              <span className="profile-info">
                <Mail size={15} />
                {profile.email || "No email"}
              </span>

              <span className="profile-info">
                <MapPin size={15} />
                {profile.hospital || "No hospital"}
              </span>
            </div>
          </div>

          <span
              className={`profile-active-badge ${
                isAvailable ? "active" : "inactive"
              }`}
            >
              {isAvailable ? "Available" : "Unavailable"}
            </span>
        </section>

        <section className="profile-section-card">
          <h3 className="profile-card-title">Location & Department</h3>

          <div className="profile-grid">
            <InfoBlock
              icon={<Building2 size={17} />}
              label="Hospital"
              value={profile.hospital || "-"}
            />

            <InfoBlock
              icon={<Stethoscope size={17} />}
              label="Department"
              value={profile.department || "-"}
            />

            <InfoBlock
              icon={<MapPin size={17} />}
              label="Location / Room"
              value={profile.location || "-"}
            />
          </div>
        </section>

        <section className="profile-section-card">
          <h3 className="profile-card-title">Availability</h3>

          <div className="profile-grid">
            <InfoBlock
              icon={<Clock size={17} />}
              label="Available Days"
              value={
                profile.availableDays.length
                  ? profile.availableDays.join(", ")
                  : "-"
              }
            />

            <InfoBlock
              icon={<Clock size={17} />}
              label="Available Time Range"
              value={`${profile.startTime} - ${profile.endTime}`}
            />
          </div>
        </section>

        <section className="profile-section-card">
          <h3 className="profile-card-title">
            Teaching / Assessing Preferences
          </h3>

          <div className="profile-grid">
            <InfoBlock
              label="Preferred Student Level"
              value={profile.studentLevel || "-"}
            />

            <InfoBlock
              label="I want to teach/assess"
              value={profile.wbaTypes || "-"}
            />
          </div>
        </section>
      </main>

      <BaseModal
        isOpen={editing}
        title="Edit Profile"
        onClose={() => setEditing(false)}
      >
        <div>
          <div>
            <div className="profile-modal-header">
              <p className="profile-modal-subtitle">
                Update your supervisor profile details
              </p>
            </div>

            <div className="profile-form-grid">
              <label className="profile-label">
                Profile Picture

                <input
                  type="file"
                  accept="image/*"
                  onChange={(e) => {
                    const file = e.target.files?.[0];

                    if (!file) return;

                    if (file.size > 5 * 1024 * 1024) {
                      alert("Please select an image smaller than 5MB.");
                      e.target.value = "";
                      return;
                    }

                    setAvatarFile(file);
                  }}
                />

                <span className="profile-file-hint">
                  Maximum file size: 5 MB
                </span>
              </label>

              <label className="profile-label">
                Name

                <input
                  className="profile-input profile-input-disabled"
                  value={form.name}
                  disabled
                />
              </label>

              <TextInput
                label="Title"
                value={form.title}
                onChange={(v) => setForm({ ...form, title: v })}
              />

              <TextInput
                label="Email"
                value={form.email}
                onChange={(v) => setForm({ ...form, email: v })}
              />

              <TextInput
                label="Hospital"
                value={form.hospital}
                onChange={(v) => setForm({ ...form, hospital: v })}
              />

              <TextInput
                label="Department"
                value={form.department}
                onChange={(v) => setForm({ ...form, department: v })}
              />

              <TextInput
                label="Location / Room"
                value={form.location}
                onChange={(v) => setForm({ ...form, location: v })}
              />

              <TextInput
                label="Preferred Student Level"
                value={form.studentLevel}
                onChange={(v) => setForm({ ...form, studentLevel: v })}
              />

              <div className="profile-full-width">
                <div className="profile-label-text">Available Days</div>

                <div className="profile-option-grid">
                  {dayOptions.map((day) => (
                    <label key={day} className="profile-checkbox-option">
                      <input
                        type="checkbox"
                        checked={form.availableDays.includes(day)}
                        onChange={() => toggleDay(day)}
                      />
                      {day}
                    </label>
                  ))}
                </div>
              </div>

              <div className="profile-time-row">
                <label className="profile-label">
                  Start Time

                  <select
                    className="profile-input"
                    value={form.startTime}
                    onChange={(e) =>
                      setForm({ ...form, startTime: e.target.value })
                    }
                  >
                    {timeOptions.map((time) => (
                      <option key={time} value={time}>
                        {time}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="profile-label">
                  End Time

                  <select
                    className="profile-input"
                    value={form.endTime}
                    onChange={(e) =>
                      setForm({ ...form, endTime: e.target.value })
                    }
                  >
                    {timeOptions.map((time) => (
                      <option key={time} value={time}>
                        {time}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <div className="profile-full-width">
                <TextInput
                  label="I want to teach/assess"
                  value={form.wbaTypes}
                  onChange={(v) => setForm({ ...form, wbaTypes: v })}
                />
              </div>
            </div>

            <div className="profile-modal-actions">
              <button
                className="profile-cancel-button"
                onClick={() => setEditing(false)}
              >
                Cancel
              </button>

              <button
                className="profile-save-button"
                onClick={saveProfile}
                disabled={saving}
              >
                {saving ? "Saving..." : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      </BaseModal>
    </div>
  );
}

function TextInput({ label, value, onChange }) {
  return (
    <label className="profile-label">
      {label}

      <input
        className="profile-input"
        value={value || ""}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

function InfoBlock({ icon, label, value }) {
  return (
    <div>
      <div className="profile-block-label">
        {icon}
        {label}
      </div>

      <div className="profile-block-value">{value}</div>
    </div>
  );
}
