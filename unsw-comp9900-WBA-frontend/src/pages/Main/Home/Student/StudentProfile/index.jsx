import "./index.css";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowLeft,
  Building2,
  Edit3,
  GraduationCap,
  Hash,
  Mail,
  MapPin,
  School,
  Stethoscope,
} from "lucide-react";
import {
  getProfile,
  updateProfile,
  uploadFile,
} from "@/config/request/api/userApi.js";
import { notify } from "@/utils/notify.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import { useUserStore } from "@/config/store/useUserStore.js";
import {EMAIL_REGEX} from "@/config/constant/RegexPatternConstant.js";

function userToProfile(user) {
  if (!user) {
    return {
      name: "",
      title: "Student",
      email: "",
      hospital: "",
      department: "",
      zid: "",
      faculty: "",
      grade: "",
      avatar: "",
    };
  }

  return {
    name: user.name || user.username || user.fullName || "Student",
    title: "Student",
    email: user.email || "",
    hospital: user.hospital || "",
    department: user.department || "",
    zid: user.zId || user.zid || user.zID || "",
    faculty: user.faculty || "",
    grade: user.grade || "",
    avatar: user.avatar || "",
  };
}

export default function StudentProfile() {
  const savedUser = useUserStore((state) => state.userInfo);
  const setUserInfo = useUserStore((state) => state.setUserInfo);

  const initialProfile = userToProfile(savedUser);
  const [searchParams] = useSearchParams();
  const studentId = searchParams.get("id");
  const viewingStudent = Boolean(studentId);
  const navigate = useNavigate();

  const [profile, setProfile] = useState(initialProfile);
  const [form, setForm] = useState(initialProfile);

  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [avatarFile, setAvatarFile] = useState(null);

  useEffect(() => {
    getProfile(studentId || "").then((data) => {
      const profileData = data.data;
      const studentProfile = userToProfile(profileData);

      setProfile(studentProfile);
      setForm(studentProfile);

      if (!studentId) {
        const currentUser = useUserStore.getState().userInfo;

        setUserInfo({
          ...currentUser,
          title: "Student",
          email: form.email,
          hospital: form.hospital,
          department: form.department,
          zId: form.zid,
          faculty: form.faculty,
          grade: form.grade,
          avatarUpdatedAt: Date.now(),
        });
      }
    });
  }, [studentId, setUserInfo]);

  const openEdit = () => {
    setForm(profile);
    setAvatarFile(null);
    setMessage("");
    setEditing(true);
  };

  const updateStudentProfile = (nextProfile, avatarUrl) => {
    const currentUser = useUserStore.getState().userInfo;

    const payload = {
      title: "Student",
      email: nextProfile.email,
      hospital: nextProfile.hospital,
      department: nextProfile.department,
      zId: nextProfile.zid,
      faculty: nextProfile.faculty,
      grade: nextProfile.grade,
      avatar: avatarUrl || "",
      roleId: currentUser?.roleId,
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

      await updateStudentProfile(form, avatarUrl);

      const updatedProfile = {
        ...form,
        title: "Student",
        avatar: avatarUrl,
      };

      setProfile(updatedProfile);
      setForm(updatedProfile);

      const currentUser = useUserStore.getState().userInfo;

      setUserInfo({
        ...currentUser,
        title: "Student",
        email: form.email,
        hospital: form.hospital,
        department: form.department,
        zid: form.zid,
        faculty: form.faculty,
        grade: form.grade,
        avatar: avatarUrl,
        avatarUpdatedAt: Date.now(),
      });

      window.dispatchEvent(new Event("userUpdated"));

      setEditing(false);
      setAvatarFile(null);
      setMessage("Profile updated successfully.");

      notify.success("Profile updated successfully.");
    } finally {
      setSaving(false);
    }
  };

  const getInitials = (name = "") => {
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
              {viewingStudent ? "Student Profile" : "My Profile"}
            </h1>

            <p className="profile-subtitle">
              {viewingStudent
                ? "View the student's profile and academic details"
                : "Manage your student profile and academic details"}
            </p>
          </div>

          {viewingStudent ? (
            <button
              className="profile-edit-button"
              onClick={() => navigate("/home/supervisor/upcoming-sessions")}
            >
              <ArrowLeft size={15} />
              Back to Upcoming Sessions
            </button>
          ) : (
            <button className="profile-edit-button" onClick={openEdit}>
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
                  alt={`${profile.name} profile`}
                  className="profile-avatar-image"
                />
              ) : (
                getInitials(profile.name)
              )}
            </div>

            <div className="profile-main-info">
              <h2 className="profile-name">{profile.name}</h2>

              <p className="profile-role">
                Student · {profile.department || "No department"}
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

            <span className="profile-active-badge">Active</span>
          </section>

          <section className="profile-section-card">
            <h3 className="profile-card-title">Student Information</h3>

            <div className="profile-grid">
              <InfoBlock
                icon={<Hash size={17} />}
                label="zID"
                value={profile.zid || "-"}
              />

              <InfoBlock
                icon={<School size={17} />}
                label="Faculty"
                value={profile.faculty || "-"}
              />

              <InfoBlock
                icon={<GraduationCap size={17} />}
                label="Grade"
                value={profile.grade || "-"}
              />
            </div>
          </section>

          <section className="profile-section-card">
            <h3 className="profile-card-title">Academic Details</h3>

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
                icon={<Mail size={17} />}
                label="Email"
                value={profile.email || "-"}
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
          <div className="profile-modal-header">
            <p className="profile-modal-subtitle">
              Update your student profile details
            </p>
          </div>

          <div className="profile-form-grid">
            <label className="profile-label">
              Profile Picture

              <input
                type="file"
                accept="image/*"
                onChange={(event) => {
                  const file = event.target.files?.[0];

                  if (!file) return;

                  if (file.size > 5 * 1024 * 1024) {
                    alert("Please select an image smaller than 5MB.");
                    event.target.value = "";
                    return;
                  }

                  setAvatarFile(file);
                }}
              />

              <span className="profile-file-hint">
                Maximum file size: 5 MB
              </span>
            </label>

            <DisabledInput label="Name" value={form.name} />

            <DisabledInput label="Title" value="Student" />

            <TextInput
                label="zID"
                value={form.zid}
                onChange={(value) =>
                  setForm((previous) => ({
                    ...previous,
                    zid: value,
                  }))
                }
              />

            <TextInput
              label="Email"
              value={form.email}
              onChange={(value) =>
                setForm((previous) => ({
                  ...previous,
                  email: value,
                }))
              }
            />

            <TextInput
              label="Hospital"
              value={form.hospital}
              onChange={(value) =>
                setForm((previous) => ({
                  ...previous,
                  hospital: value,
                }))
              }
            />

            <TextInput
              label="Department"
              value={form.department}
              onChange={(value) =>
                setForm((previous) => ({
                  ...previous,
                  department: value,
                }))
              }
            />

            <TextInput
              label="Faculty"
              value={form.faculty}
              onChange={(value) =>
                setForm((previous) => ({
                  ...previous,
                  faculty: value,
                }))
              }
            />

            <TextInput
              label="Grade"
              value={form.grade}
              onChange={(value) =>
                setForm((previous) => ({
                  ...previous,
                  grade: value,
                }))
              }
            />
          </div>

          <div className="profile-modal-actions">
            <button
              className="profile-cancel-button"
              onClick={() => setEditing(false)}
              disabled={saving}
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
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function DisabledInput({ label, value }) {
  return (
    <label className="profile-label">
      {label}

      <input
        className="profile-input profile-input-disabled"
        value={value || ""}
        disabled
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
