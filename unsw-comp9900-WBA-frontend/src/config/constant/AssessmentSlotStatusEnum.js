const assessmentSlotStatusEnum = {
    AVAILABLE: { code: 0, status: "open" },
    PENDING: { code: 1, status: "booked" },
    COMPLETED: { code: 2, status: "completed" },
};

const codeMap = {
    0: assessmentSlotStatusEnum.AVAILABLE,
    1: assessmentSlotStatusEnum.PENDING,
    2: assessmentSlotStatusEnum.COMPLETED
};

function getEnumByCode(code) {
    return codeMap[code];
}

export { getEnumByCode };
export default assessmentSlotStatusEnum;
