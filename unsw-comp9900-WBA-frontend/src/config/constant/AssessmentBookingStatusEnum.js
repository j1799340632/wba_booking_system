const assessmentBookingStatusEnum = {
    PENDING: { code: 0, status: "Pending" },
    CONFIRMED: { code: 1, status: "Confirmed" },
    COMPLETED: { code: 2, status: "Completed" },
    DECLINED: { code: 3, status: "Declined" },
    CANCELED: { code: 4, status: "Canceled" },
};

const codeMap = {
    0: assessmentBookingStatusEnum.PENDING,
    1: assessmentBookingStatusEnum.CONFIRMED,
    2: assessmentBookingStatusEnum.COMPLETED,
    3: assessmentBookingStatusEnum.DECLINED,
    4: assessmentBookingStatusEnum.CANCELED,
};

function getEnumByCode(code) {
    return codeMap[code];
}

export { getEnumByCode };
export default assessmentBookingStatusEnum;
