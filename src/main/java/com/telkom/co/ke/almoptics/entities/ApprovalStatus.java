
package com.telkom.co.ke.almoptics.entities;

/**
 * Enum representing the possible approval statuses in the workflow.
 */
public enum ApprovalStatus {
    PENDING,         // Waiting for approval
    APPROVED,        // Change has been approved
    REJECTED,        // Change has been rejected
    PENDING_DELETION // Marked for deletion, awaiting approval
}
