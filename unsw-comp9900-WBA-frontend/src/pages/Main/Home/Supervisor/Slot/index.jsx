import "./index.css";
import { useEffect, useRef, useState } from "react";
import {
  Plus,
  Trash2,
  MapPin,
  BookOpen,
  Search,
  Repeat2,
  Pencil,
} from "lucide-react";

import {
  delAssessmentSlot,
  getAssessmentSlotList,
  saveAssessmentSlot,
  toggleAssessmentSlotsEnableDisable,
} from "@/config/request/api/assessmentSlotApi.js";

import { notify } from "@/utils/notify.js";
import { useConfirmStore } from "@/config/store/useConfirmStore.js";
import { useUserStore } from "@/config/store/useUserStore.js";
import AssessmentSlotRepeatTypeEnum from "@/config/constant/AssessmentSlotRepeatTypeEnum.js";
import {Loading} from "@/components/Loading/index.jsx";
import {getEnabledDepartments, getEnabledHospitals, getEnabledWbaTypes} from "@/config/request/api/dictionaryApi.js";

const TIMES = [
  "07:00",
  "07:30",
  "08:00",
  "08:30",
  "09:00",
  "09:30",
  "10:00",
  "10:30",
  "11:00",
  "11:30",
  "12:00",
  "12:30",
  "13:00",
  "13:30",
  "14:00",
  "14:30",
  "15:00",
  "15:30",
  "16:00",
  "16:30",
  "17:00",
  "17:30",
  "18:00",
  "18:30",
  "19:00",
  "19:30",
  "20:00",
];

const WEEKDAYS = [
  { value: "MO", label: "Mon" },
  { value: "TU", label: "Tue" },
  { value: "WE", label: "Wed" },
  { value: "TH", label: "Thu" },
  { value: "FR", label: "Fri" },
  { value: "SA", label: "Sat" },
  { value: "SU", label: "Sun" },
];

function WBABadge({ type }) {
  if (!type) {
    return null;
  }

  return (
    <span className="availability-wba-badge">
      {type}
    </span>
  );
}

function RepeatBadge() {
  return (
    <span className="availability-repeat-badge">
      <Repeat2 size={12} />
      REPEAT
    </span>
  );
}

export default function SlotsSupervisor() {
  const [hoverAdd, setHoverAdd] = useState(false);
  const [hoverCancel, setHoverCancel] = useState(false);

  const [slots, setSlots] = useState([]);
  const [hospitalSelectList, setHospitalSelectList] = useState([]);
  const [departmentSelectList, setDepartmentSelectList] = useState([]);
  const [wbaTypeSelectList, setWbaTypeSelectList] = useState([]);

  const [showAddSlot, setShowAddSlot] =
    useState(false);

  const [isRepeatMode, setIsRepeatMode] =
    useState(false);

  const [editingSlotId, setEditingSlotId] =
    useState(null);

  const [editingSlot, setEditingSlot] =
    useState(null);

  const [
    hoveredDeleteId,
    setHoveredDeleteId,
  ] = useState(null);

  const [searchText, setSearchText] =
    useState("");

  const [timeSort, setTimeSort] =
    useState("earliest");

  const [currentPage, setCurrentPage] =
    useState(1);

  const [updatingEnableId, setUpdatingEnableId] =
    useState(null);

  const getToday = () => {
    const date = new Date();

    const year = date.getFullYear();

    const month = String(
      date.getMonth() + 1
    ).padStart(2, "0");

    const day = String(
      date.getDate()
    ).padStart(2, "0");

    return `${year}-${month}-${day}`;
  };

  const today = getToday();

  // Shared slot fields
  const [newStartTime, setNewStartTime] =
    useState("09:00");

  const [newEndTime, setNewEndTime] =
    useState("09:30");

  const [newLocation, setNewLocation] =
    useState("");

  const [newDepartment, setNewDepartment] =
    useState("");

  const [newType, setNewType] =
    useState("");

  const [newHospital, setNewHospital] =
    useState("");

  // Single slot
  const [newDate, setNewDate] =
    useState(today);

  // Repeat range
  const [
    repeatStartDate,
    setRepeatStartDate,
  ] = useState(today);

  const [
    repeatStartTime,
    setRepeatStartTime,
  ] = useState("00:00");

  const [
    repeatEndDate,
    setRepeatEndDate,
  ] = useState(today);

  const [
    repeatEndTime,
    setRepeatEndTime,
  ] = useState("23:59");

  // 1 = Weekly, 2 = Fortnightly
  const [
    repeatIntervalWeeks,
    setRepeatIntervalWeeks,
  ] = useState(1);

  const [
    selectedWeekdays,
    setSelectedWeekdays,
  ] = useState([]);

  const showConfirm = useConfirmStore(
    (state) => state.showConfirm
  );

  const user = useUserStore(
    (state) => state.userInfo
  );

  const pageSize = 10;
  const topRef = useRef(null);

  const formatDateTime = (date, time) => {
    return `${date} ${time}:00`;
  };

  const isRepeatSlot = (slot) => {
    const noRepeatCode =
      AssessmentSlotRepeatTypeEnum.NO_REPEAT.code;

    return Boolean(
      slot.rrule ||
        slot.repeatRule ||
        slot.isRepeat ||
        slot.repeat ||
        (slot.repeatType !== undefined &&
          slot.repeatType !== null &&
          slot.repeatType !== noRepeatCode) ||
        slot.daysOfWeek?.length > 0
    );
  };

  const resetForm = () => {
    setNewDate(getToday());
    setNewStartTime("09:00");
    setNewEndTime("09:30");
    setNewLocation("");
    setNewType("");
    setNewHospital("");
    setNewDepartment("");

    setRepeatStartDate(getToday());
    setRepeatStartTime("00:00");
    setRepeatEndDate(getToday());
    setRepeatEndTime("23:59");
    setRepeatIntervalWeeks(1);
    setSelectedWeekdays([]);

    setIsRepeatMode(false);
  };

  const closeForm = () => {
    resetForm();
    setEditingSlotId(null);
    setEditingSlot(null);
    setShowAddSlot(false);
  };

  const openAddSlotForm = () => {
    resetForm();
    setEditingSlotId(null);
    setEditingSlot(null);
    setShowAddSlot(true);

    setTimeout(() => {
      topRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }, 0);
  };

  const toggleWeekday = (weekday) => {
    setSelectedWeekdays((current) => {
      if (current.includes(weekday)) {
        return current.filter(
          (day) => day !== weekday
        );
      }

      return [...current, weekday];
    });
  };

  const loadSlots = () => {
    Loading.show()

    const supervisorId = user?.id;

    getAssessmentSlotList(
      1,
      -1,
      supervisorId
    )
      .then((data) => {
        const records =
          data?.data?.records || [];

        const formattedSlots = records.map(
          (slot) => ({
            // Not lose fields
            ...slot,
            id: slot.id,

            date:
              slot.startTime?.slice(0, 10) ||
              "",

            time:
              slot.startTime?.slice(11, 16) ||
              "",

            duration: slot.duration || 45,
            location: slot.location || "",
            department: slot.department || "",
            hospital: slot.hospital || "",
            type: slot.type || "",

            creatorName:
              slot.creatorName ||
              "Supervisor",

            status: slot.status,


            isEnable:
              slot.isEnable ??
              slot.is_enable ??
              true,

            // Repeat information
            repeatType: slot.repeatType,

            repeatTypeName:
              slot.repeatTypeName,

            interval:
              slot.interval ??
              slot.repeatInterval,

            daysOfWeek:
              slot.daysOfWeek || [],

            rrule: slot.rrule,

            repeatRule:
              slot.repeatRule,

            isRepeat:
              slot.isRepeat,

            repeat:
              slot.repeat,

            repeatStartTime:
              slot.repeatStartTime,

            repeatEndTime:
              slot.repeatEndTime,
          })
        );

        setSlots(formattedSlots);
      })
      .catch((error) => {
        console.error(
          "Failed to load slots:",
          error
        );
      })
      .finally(() => {
        Loading.hide()
      });
  };

  useEffect(() => {
    if (user?.id) {
      loadSlots();
    }
    getEnabledHospitals().then(res => setHospitalSelectList(res.data))
    getEnabledDepartments().then(res => setDepartmentSelectList(res.data))
    getEnabledWbaTypes().then(res => setWbaTypeSelectList(res.data))
  }, [user?.id]);

  useEffect(() => {
    window.scrollTo({
      top: 0,
      behavior: "smooth",
    });
  }, [currentPage]);

  const filteredSlots = slots.filter(
    (slot) => {
      const keyword = searchText
        .trim()
        .toLowerCase();

      const repeatText = isRepeatSlot(slot)
        ? "repeat repeated weekly fortnightly"
        : "";

      const enabledText = slot.isEnable
        ? "enabled open active"
        : "disabled closed inactive";

      return (
        !keyword ||
        slot.date
          ?.toLowerCase()
          .includes(keyword) ||
        slot.time
          ?.toLowerCase()
          .includes(keyword) ||
        slot.location
          ?.toLowerCase()
          .includes(keyword) ||
        slot.department
          ?.toLowerCase()
          .includes(keyword) ||
        slot.hospital
          ?.toLowerCase()
          .includes(keyword) ||
        slot.type
          ?.toLowerCase()
          .includes(keyword) ||
        slot.creatorName
          ?.toLowerCase()
          .includes(keyword) ||
        slot.repeatTypeName
          ?.toLowerCase()
          .includes(keyword) ||
        repeatText.includes(keyword) ||
        enabledText.includes(keyword)
      );
    }
  );

  const sortedSlots = [
    ...filteredSlots,
  ].sort((a, b) => {
    const timeA = new Date(
      `${a.date}T${a.time}:00`
    ).getTime();

    const timeB = new Date(
      `${b.date}T${b.time}:00`
    ).getTime();

    return timeSort === "latest"
      ? timeB - timeA
      : timeA - timeB;
  });

  const totalPages = Math.ceil(
    sortedSlots.length / pageSize
  );

  const paginatedSlots = sortedSlots.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );

  const validateRequiredFields = () => {
    if (!newHospital) {
      notify.error(
        "Hospital is required."
      );
      return false;
    }

    if (!newDepartment) {
      notify.error(
        "Department is required."
      );
      return false;
    }

    if (!newLocation) {
      notify.error(
        "Location is required."
      );
      return false;
    }

    if (!newType) {
      notify.error(
        "WBA type is required."
      );
      return false;
    }

    return true;
  };

  const validateSlotTime = (
    date,
    checkFuture = true
  ) => {
    const slotStartDateTime =
      new Date(
        `${date}T${newStartTime}:00`
      );

    const slotEndDateTime =
      new Date(
        `${date}T${newEndTime}:00`
      );

    if (
      Number.isNaN(
        slotStartDateTime.getTime()
      ) ||
      Number.isNaN(
        slotEndDateTime.getTime()
      )
    ) {
      notify.error(
        "Please enter a valid date and time."
      );
      return false;
    }

    if (
      checkFuture &&
      slotStartDateTime <= new Date()
    ) {
      notify.error(
        "Slot starting time must be in the future."
      );
      return false;
    }

    if (
      slotEndDateTime <=
      slotStartDateTime
    ) {
      notify.error(
        "Slot ending time must be later than slot starting time."
      );
      return false;
    }

    return true;
  };

  const finishCreatingSlot = (
    isRepeated
  ) => {
    notify.success(
      isRepeated
        ? "Repeated slot added successfully!"
        : "Slot added successfully!"
    );

    loadSlots();
    resetForm();
    setEditingSlotId(null);
    setEditingSlot(null);
    setShowAddSlot(false);
    setCurrentPage(1);
  };

  const addSingleSlot = async () => {
    if (!validateRequiredFields()) {
      return;
    }

    if (!validateSlotTime(newDate)) {
      return;
    }

    const body = {
      startTime: formatDateTime(
        newDate,
        newStartTime
      ),

      endTime: formatDateTime(
        newDate,
        newEndTime
      ),

      repeatType:
        AssessmentSlotRepeatTypeEnum
          .NO_REPEAT.code,

      hospital: newHospital,
      department: newDepartment,
      location: newLocation,
      type: newType,
    };

    try {
      await saveAssessmentSlot(body);

      finishCreatingSlot(false);
    } catch (error) {
      console.error(
        "Failed to create slot:",
        error
      );

      notify.error(
        "Failed to create the slot."
      );
    }
  };

  const addRepeatSlots = async () => {
    if (!validateRequiredFields()) {
      return;
    }

    if (
      !repeatStartDate ||
      !repeatEndDate
    ) {
      notify.error(
        "Repeat start date and repeat end date are required."
      );
      return;
    }

    if (
      !repeatStartTime ||
      !repeatEndTime
    ) {
      notify.error(
        "Repeat start time and repeat end time are required."
      );
      return;
    }

    if (
      selectedWeekdays.length === 0
    ) {
      notify.error(
        "Please select at least one weekday."
      );
      return;
    }

    if (
      !Number.isInteger(
        repeatIntervalWeeks
      ) ||
      repeatIntervalWeeks < 1
    ) {
      notify.error(
        "Repeat interval must be at least 1."
      );
      return;
    }

    const repeatRangeStart =
      new Date(
        `${repeatStartDate}T${repeatStartTime}:00`
      );

    const repeatRangeEnd =
      new Date(
        `${repeatEndDate}T${repeatEndTime}:00`
      );

    if (
      repeatRangeEnd <=
      repeatRangeStart
    ) {
      notify.error(
        "Repeat end date and time must be later than repeat start date and time."
      );
      return;
    }

    if (
      !validateSlotTime(
        repeatStartDate
      )
    ) {
      return;
    }

    const body = {
      startTime: formatDateTime(
        repeatStartDate,
        newStartTime
      ),

      endTime: formatDateTime(
        repeatStartDate,
        newEndTime
      ),

      repeatStartTime:
        formatDateTime(
          repeatStartDate,
          repeatStartTime
        ),

      repeatEndTime:
        formatDateTime(
          repeatEndDate,
          repeatEndTime
        ),

      repeatType:
        AssessmentSlotRepeatTypeEnum
          .WEEKLY.code,

      interval:
        repeatIntervalWeeks,

      daysOfWeek:
        selectedWeekdays,

      hospital: newHospital,
      department: newDepartment,
      location: newLocation,
      type: newType,
    };

    try {
      await saveAssessmentSlot(body);

      finishCreatingSlot(true);
    } catch (error) {
      console.error(
        "Failed to create repeated slot:",
        error
      );

      notify.error(
        "Failed to create the repeated slot."
      );
    }
  };


  const toggleSlotEnabled = async (slot) => {
    if (updatingEnableId === slot.id) {
      return;
    }

    const previousValue = slot.isEnable;
    const nextValue = !previousValue;

    setUpdatingEnableId(slot.id);


    setSlots((currentSlots) =>
      currentSlots.map((currentSlot) =>
        currentSlot.id === slot.id
          ? {
              ...currentSlot,
              isEnable: nextValue,
            }
          : currentSlot
      )
    );

    try {
      await toggleAssessmentSlotsEnableDisable(
        slot.id,
        nextValue
      );

      notify.success(
        nextValue
          ? "Slot enabled successfully."
          : "Slot disabled successfully."
      
      );


      setSlots((currentSlots) =>
        currentSlots.map((currentSlot) =>
          currentSlot.id === slot.id
            ? {
                ...currentSlot,
                isEnable: previousValue,
              }
            : currentSlot
        )
      );

      notify.error(
        "Failed to update the slot status."
      );
    } finally {
      setUpdatingEnableId(null);
    }
  };

  const handleEditSlot = (slot) => {
    const repeatSlot = isRepeatSlot(slot);

    const startDateTime = new Date(
      `${slot.date}T${slot.time}:00`
    );

    startDateTime.setMinutes(
      startDateTime.getMinutes() +
        Number(slot.duration || 30)
    );

    const endHour = String(
      startDateTime.getHours()
    ).padStart(2, "0");

    const endMinute = String(
      startDateTime.getMinutes()
    ).padStart(2, "0");

    const calculatedEndTime =
      `${endHour}:${endMinute}`;

    const validEndTime =
      TIMES.includes(calculatedEndTime)
        ? calculatedEndTime
        : TIMES.find(
            (time) => time > slot.time
          ) || "20:00";

    setEditingSlotId(slot.id);
    setEditingSlot(slot);

    setNewDate(slot.date);
    setNewStartTime(slot.time);
    setNewEndTime(validEndTime);

    setNewHospital(
      slot.hospital || ""
    );

    setNewLocation(
      slot.location || ""
    );

    setNewDepartment(
      slot.department || ""
    );

    setNewType(slot.type || "");

    if (repeatSlot) {
      const parsedRepeatStart =
        parseDateTime(
          slot.repeatStartTime,
          slot.date,
          "00:00"
        );

      const parsedRepeatEnd =
        parseDateTime(
          slot.repeatEndTime,
          slot.date,
          "23:59"
        );

      setRepeatStartDate(
        parsedRepeatStart.date
      );

      setRepeatStartTime(
        parsedRepeatStart.time
      );

      setRepeatEndDate(
        parsedRepeatEnd.date
      );

      setRepeatEndTime(
        parsedRepeatEnd.time
      );

      setRepeatIntervalWeeks(
        Number(slot.interval) || 1
      );

      setSelectedWeekdays(
        Array.isArray(slot.daysOfWeek)
          ? slot.daysOfWeek
          : []
      );
    } else {
      setRepeatStartDate(
        slot.date || getToday()
      );

      setRepeatStartTime("00:00");

      setRepeatEndDate(
        slot.date || getToday()
      );

      setRepeatEndTime("23:59");

      setRepeatIntervalWeeks(1);
      setSelectedWeekdays([]);
    }

    setIsRepeatMode(repeatSlot);
    setShowAddSlot(true);

    setTimeout(() => {
      topRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }, 0);
  };

  const updateSlot = async () => {
    if (!validateRequiredFields()) {
      return;
    }

    if (!validateSlotTime(newDate, false)) {
      return;
    }

    const body = {

      id: editingSlotId,

      startTime: formatDateTime(
        newDate,
        newStartTime
      ),

      endTime: formatDateTime(
        newDate,
        newEndTime
      ),


      // Preserve the original repeat information where available.
      repeatType:
        editingSlot?.repeatType ??
        AssessmentSlotRepeatTypeEnum
          .NO_REPEAT.code,

      interval:
        editingSlot?.interval,

      daysOfWeek:
        editingSlot?.daysOfWeek,

      repeatStartTime:
        editingSlot?.repeatStartTime,

      repeatEndTime:
        editingSlot?.repeatEndTime,

      hospital: newHospital,
      department: newDepartment,
      location: newLocation,
      type: newType,
      // Optimistic update(required)
      version: editingSlot.version,
    };

    try {
      await saveAssessmentSlot(body);

      notify.success(
        "Slot updated successfully!"
      );

      loadSlots();
      closeForm();
    } catch (error) {
      console.error(
        "Failed to update slot:",
        error
      );
    }
  };

  const parseDateTime = (
    dateTime,
    fallbackDate,
    fallbackTime
  ) => {
    if (!dateTime) {
      return {
        date: fallbackDate,
        time: fallbackTime,
      };
    }

    const normalized = String(dateTime)
      .replace(" ", "T");

    const [datePart, timePart = ""] =
      normalized.split("T");

    return {
      date: datePart || fallbackDate,
      time:
        timePart.slice(0, 5) ||
        fallbackTime,
    };
  };

  const removeSlot = (slotId) => {
    showConfirm(
      "Reminder",
      "Are you sure you want to delete this available slot?",
      () => {
        delAssessmentSlot(slotId)
          .then(() => {
            notify.success(
              "Slot deleted successfully!"
            );

            loadSlots();
          })
          .catch((error) => {
            console.error(
              "Failed to delete slot:",
              error
            );

            notify.error(
              "Failed to delete the slot."
            );
          });
      }
    );
  };

  return (
    <div>
      <main className="availability-main">
        <div
          ref={topRef}
          className="availability-container"
        >
          <div className="availability-header">
            <div>
              <h1 className="availability-title">
                My Slots
              </h1>

              <p className="availability-subtitle">
                Manage your assessment slots
              </p>
            </div>

            <button
              type="button"
              onClick={openAddSlotForm}
              className="availability-add-button"
            >
              <Plus size={16} />
              Add Slot
            </button>
          </div>

          <div className="availability-search-row">
            <div className="availability-search-box">
              <Search
                size={15}
                color="#94A3B8"
              />

              <input
                className="availability-search-input"
                placeholder="Search availability..."
                value={searchText}
                onChange={(event) => {
                  setSearchText(
                    event.target.value
                  );

                  setCurrentPage(1);
                }}
              />
            </div>

            <div className="availability-filter-group">
              <select
                className="availability-filter-select"
                value={timeSort}
                onChange={(event) => {
                  setTimeSort(
                    event.target.value
                  );

                  setCurrentPage(1);
                }}
              >
                <option value="earliest">
                  Earliest first
                </option>

                <option value="latest">
                  Latest first
                </option>
              </select>

              <span className="availability-result-count">
                {filteredSlots.length} slots
              </span>
            </div>
          </div>

          {showAddSlot && (
            <div className="availability-add-slot-card">
              <div className="availability-form-heading-row">
                <div>
                  <h3 className="availability-form-title">
                    {editingSlotId
                      ? "Edit Available Slot"
                      : isRepeatMode
                        ? "New Repeated Slots"
                        : "New Available Slot"}
                  </h3>

                  <p className="availability-form-subtitle">
                    {editingSlotId
                      ? "Update the details of this assessment slot."
                      : isRepeatMode
                        ? "Create weekly or fortnightly slots on selected weekdays."
                        : "Create one availability slot for a specific date."}
                  </p>
                </div>

                {editingSlotId === null && (
                  <div className="availability-mode-switch">
                    <button
                      type="button"
                      className={`availability-mode-button ${
                        !isRepeatMode
                          ? "is-active"
                          : ""
                      }`}
                      onClick={() =>
                        setIsRepeatMode(
                          false
                        )
                      }
                    >
                      Single Slot
                    </button>

                    <button
                      type="button"
                      className={`availability-mode-button ${
                        isRepeatMode
                          ? "is-active"
                          : ""
                      }`}
                      onClick={() =>
                        setIsRepeatMode(
                          true
                        )
                      }
                    >
                      <Repeat2 size={15} />
                      Repeat Slot
                    </button>
                  </div>
                )}
              </div>

              <div className="availability-form-grid">
                {!isRepeatMode ? (
                  <label className="availability-label">
                    Date

                    <input
                      type="date"
                      value={newDate}
                      min={
                        editingSlotId !== null
                          ? undefined
                          : today
                      }
                      onChange={(event) =>
                        setNewDate(
                          event.target.value
                        )
                      }
                      className="availability-input"
                    />
                  </label>
                ) : (
                  <>
                    <div className="availability-label">
                      Repeat Start Date and Time

                      <div className="availability-datetime-row">
                        <input
                          type="date"
                          value={
                            repeatStartDate
                          }
                          min={today}
                          onChange={(
                            event
                          ) => {
                            const value =
                              event.target
                                .value;

                            setRepeatStartDate(
                              value
                            );

                            if (
                              repeatEndDate <
                              value
                            ) {
                              setRepeatEndDate(
                                value
                              );
                            }
                          }}
                          className="availability-input"
                        />

                        <input
                          type="time"
                          value={
                            repeatStartTime
                          }
                          onChange={(
                            event
                          ) =>
                            setRepeatStartTime(
                              event.target
                                .value
                            )
                          }
                          className="availability-input"
                        />
                      </div>
                    </div>

                    <div className="availability-label">
                      Repeat End Date and Time

                      <div className="availability-datetime-row">
                        <input
                          type="date"
                          value={
                            repeatEndDate
                          }
                          min={
                            repeatStartDate ||
                            today
                          }
                          onChange={(
                            event
                          ) =>
                            setRepeatEndDate(
                              event.target
                                .value
                            )
                          }
                          className="availability-input"
                        />

                        <input
                          type="time"
                          value={
                            repeatEndTime
                          }
                          onChange={(
                            event
                          ) =>
                            setRepeatEndTime(
                              event.target
                                .value
                            )
                          }
                          className="availability-input"
                        />
                      </div>
                    </div>

                    <label className="availability-label">
                      Repeat Every

                      <select
                        value={
                          repeatIntervalWeeks
                        }
                        onChange={(
                          event
                        ) =>
                          setRepeatIntervalWeeks(
                            Number(
                              event.target
                                .value
                            )
                          )
                        }
                        className="availability-input"
                      >
                        <option value={1}>
                          1 week (Weekly)
                        </option>

                        <option value={2}>
                          2 weeks
                          (Fortnightly)
                        </option>
                      </select>
                    </label>

                    <div className="availability-weekday-field">
                      <span className="availability-label">
                        Repeat On
                      </span>

                      <div className="availability-weekday-options">
                        {WEEKDAYS.map(
                          (day) => (
                            <button
                              key={
                                day.value
                              }
                              type="button"
                              className={`availability-weekday-button ${
                                selectedWeekdays.includes(
                                  day.value
                                )
                                  ? "is-selected"
                                  : ""
                              }`}
                              onClick={() =>
                                toggleWeekday(
                                  day.value
                                )
                              }
                            >
                              {day.label}
                            </button>
                          )
                        )}
                      </div>
                    </div>
                  </>
                )}

                <label className="availability-label">
                  Slot Starting Time

                  <select
                    value={newStartTime}
                    onChange={(event) => {
                      const startTime =
                        event.target.value;

                      setNewStartTime(
                        startTime
                      );

                      const nextEndTime =
                        TIMES.find(
                          (time) =>
                            time >
                            startTime
                        );

                      if (nextEndTime) {
                        setNewEndTime(
                          nextEndTime
                        );
                      }
                    }}
                    className="availability-input"
                  >
                    {TIMES.slice(
                      0,
                      -1
                    ).map((time) => (
                      <option
                        key={time}
                        value={time}
                      >
                        {time}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="availability-label">
                  Slot Ending Time

                  <select
                    value={newEndTime}
                    onChange={(event) =>
                      setNewEndTime(
                        event.target.value
                      )
                    }
                    className="availability-input"
                  >
                    {TIMES.filter(
                      (time) =>
                        time >
                        newStartTime
                    ).map((time) => (
                      <option
                        key={time}
                        value={time}
                      >
                        {time}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="availability-label">
                  Location

                  <input
                    type="text"
                    placeholder="Enter location"
                    value={newLocation}
                    onChange={(event) =>
                      setNewLocation(
                        event.target.value
                      )
                    }
                    className="availability-input"
                  />
                </label>

                <label className="availability-label">
                  Hospital

                  <select
                    value={newHospital}
                    onChange={(event) =>
                      setNewHospital(
                        event.target.value
                      )
                    }
                    className="availability-input"
                  >
                    <option value="">
                      Select a hospital
                    </option>

                    {hospitalSelectList.map(
                      (hospital) => (
                        <option
                          key={hospital.id}
                          value={hospital.name}
                        >
                          {hospital.name}
                        </option>
                      )
                    )}
                  </select>
                </label>

                <label className="availability-label">
                  WBA Type

                  <select
                      value={newType}
                      onChange={(event) =>
                          setNewType(
                              event.target.value
                          )
                      }
                      className="availability-input"
                  >
                    <option value="">
                      Select a WBA Type
                    </option>

                    {wbaTypeSelectList.map(
                        (wbaType) => (
                            <option
                                key={wbaType.id}
                                value={wbaType.name}
                            >
                              {wbaType.name}
                            </option>
                        )
                    )}
                  </select>
                </label>

                <label className="availability-label">
                  Department

                  <select
                      value={newDepartment}
                      onChange={(event) =>
                          setNewDepartment(
                              event.target.value
                          )
                      }
                      className="availability-input"
                  >
                    <option value="">
                      Select a department
                    </option>

                    {departmentSelectList.map(
                        (department) => (
                            <option
                                key={department.id}
                                value={department.name}
                            >
                              {department.name}
                            </option>
                        )
                    )}
                  </select>
                </label>
              </div>

              <div className="availability-form-actions">
                <button
                  type="button"
                  onClick={closeForm}
                  onMouseEnter={() =>
                    setHoverCancel(true)
                  }
                  onMouseLeave={() =>
                    setHoverCancel(false)
                  }
                  className={`availability-cancel-button ${
                    hoverCancel
                      ? "is-hovered"
                      : ""
                  }`}
                >
                  Cancel
                </button>

                <button
                  type="button"
                  onClick={
                    editingSlotId !== null
                      ? updateSlot
                      : isRepeatMode
                        ? addRepeatSlots
                        : addSingleSlot
                  }
                  onMouseEnter={() =>
                    setHoverAdd(true)
                  }
                  onMouseLeave={() =>
                    setHoverAdd(false)
                  }
                  className={`availability-submit-button ${
                    hoverAdd
                      ? "is-hovered"
                      : ""
                  }`}
                >
                  {editingSlotId
                    ? "Save Changes"
                    : isRepeatMode
                      ? "Add Repeated Slots"
                      : "Add Slot"}
                </button>
              </div>
            </div>
          )}

          <div className="availability-list">
            {paginatedSlots.map(
              (slot) => {
                const date = new Date(
                  `${slot.date}T00:00:00`
                );

                const isDeleteHovered =
                  hoveredDeleteId ===
                  slot.id;

                return (
                  <div
                    key={slot.id}
                    className={`availability-slot-card ${
                      !slot.isEnable
                        ? "is-disabled"
                        : ""
                    }`}
                  >
                    <div className="availability-date-box">
                      <div className="availability-date-month">
                        {date
                          .toLocaleDateString(
                            "en-AU",
                            {
                              month:
                                "short",
                            }
                          )
                          .toUpperCase()}
                      </div>

                      <div className="availability-date-day">
                        {date.getDate()}
                      </div>

                      <div className="availability-date-weekday">
                        {date.toLocaleDateString(
                          "en-AU",
                          {
                            weekday:
                              "short",
                          }
                        )}
                      </div>
                    </div>

                    <div className="availability-slot-content">
                      <div className="availability-slot-top">
                        <strong>
                          {slot.time}
                        </strong>

                        <span className="availability-duration">
                          {slot.duration}
                          min
                        </span>

                        <WBABadge
                          type={slot.type}
                        />

                        {isRepeatSlot(
                          slot
                        ) && (
                          <RepeatBadge />
                        )}
                      </div>

                      <div className="availability-slot-meta">
                        <span className="availability-meta-item">
                          <MapPin
                            size={14}
                          />

                          {slot.hospital ||
                            "No hospital"}
                        </span>

                        <span className="availability-meta-item">
                          <MapPin
                            size={14}
                          />

                          {slot.location ||
                            "No location"}
                        </span>

                        <span className="availability-meta-item">
                          <BookOpen
                            size={14}
                          />

                          {slot.department ||
                            "No department"}
                        </span>
                      </div>
                    </div>

                    <div className="availability-slot-actions">
                      <button
                        type="button"
                        className="availability-edit-button"
                        onClick={() =>
                          handleEditSlot(
                            slot
                          )
                        }
                        title="Edit slot"
                      >
                        <Pencil
                          size={15}
                        />

                        <span>
                          Edit
                        </span>
                      </button>

                      <div className="availability-enable-control">
                        <button
                          type="button"
                          role="switch"
                          aria-checked={
                            slot.isEnable
                          }
                          className={`availability-enable-switch ${
                            slot.isEnable
                              ? "is-enabled"
                              : "is-disabled"
                          }`}
                          onClick={() =>
                            toggleSlotEnabled(
                              slot
                            )
                          }
                          title={
                            slot.isEnable
                              ? "Disable slot"
                              : "Enable slot"
                          }
                        >
                          <span className="availability-switch-thumb" />
                        </button>

                        <span
                            className={`availability-enable-text ${
                              slot.isEnable
                                ? "is-enabled"
                                : "is-disabled"
                            }`}
                          >
                            {updatingEnableId === slot.id
                              ? "Updating..."
                              : slot.isEnable
                                ? "Enabled"
                                : "Disabled"}
                          </span>
                      </div>

                      <button
                        type="button"
                        onClick={() =>
                          removeSlot(
                            slot.id
                          )
                        }
                        onMouseEnter={() =>
                          setHoveredDeleteId(
                            slot.id
                          )
                        }
                        onMouseLeave={() =>
                          setHoveredDeleteId(
                            null
                          )
                        }
                        title="Delete slot"
                        className={`availability-delete-button ${
                          isDeleteHovered
                            ? "is-hovered"
                            : ""
                        }`}
                      >
                        <Trash2
                          size={16}
                        />
                      </button>
                    </div>
                  </div>
                );
              }
            )}

            {filteredSlots.length ===
              0 && (
              <div className="availability-empty-box">
                No slots found.
              </div>
            )}
          </div>

          {filteredSlots.length >
            0 && (
            <div className="availability-pagination">
              <button
                type="button"
                className={`availability-page-button ${
                  currentPage === 1
                    ? "is-disabled"
                    : ""
                }`}
                disabled={
                  currentPage === 1
                }
                onClick={() =>
                  setCurrentPage(
                    (page) =>
                      page - 1
                  )
                }
              >
                Previous
              </button>

              <span className="availability-page-text">
                Page {currentPage} of{" "}
                {totalPages || 1}
              </span>

              <button
                type="button"
                className={`availability-page-button ${
                  currentPage ===
                    totalPages ||
                  totalPages === 0
                    ? "is-disabled"
                    : ""
                }`}
                disabled={
                  currentPage ===
                    totalPages ||
                  totalPages === 0
                }
                onClick={() =>
                  setCurrentPage(
                    (page) =>
                      page + 1
                  )
                }
              >
                Next
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
