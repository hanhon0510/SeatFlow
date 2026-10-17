import {
  CheckCircleFilled,
  CloseCircleFilled,
  ExclamationCircleFilled,
} from '@ant-design/icons'
import { Typography } from 'antd'
import type { ReactNode } from 'react'

import type { CheckInOutcome, CheckInResult } from '../types'

type Verdict = {
  tone: 'success' | 'warning' | 'error'
  icon: ReactNode
  headline: string
  advice: string
}

// The verdict is spelled out in words and an icon as well as colour, so it reads at a glance
// from arm's length and does not depend on telling green from red.
const verdicts: Record<CheckInOutcome, Verdict> = {
  CHECKED_IN: {
    tone: 'success',
    icon: <CheckCircleFilled />,
    headline: 'Admit',
    advice: 'Ticket is valid and is now checked in.',
  },
  ALREADY_USED: {
    tone: 'warning',
    icon: <ExclamationCircleFilled />,
    headline: 'Already checked in',
    advice: 'Do not admit. This ticket has already been scanned.',
  },
  CANCELLED: {
    tone: 'error',
    icon: <CloseCircleFilled />,
    headline: 'Ticket cancelled',
    advice: 'Do not admit. The order was refunded.',
  },
  WRONG_EVENT: {
    tone: 'error',
    icon: <CloseCircleFilled />,
    headline: 'Wrong event',
    advice: 'Do not admit. This ticket is for a different event.',
  },
  NOT_FOUND: {
    tone: 'error',
    icon: <CloseCircleFilled />,
    headline: 'Ticket not recognised',
    advice: 'Do not admit. Scan again or type the code in.',
  },
}

export function CheckInResultPanel({ result }: { result: CheckInResult }) {
  const verdict = verdicts[result.outcome]
  const seat = seatDescription(result)

  return (
    <div aria-live="polite" className={`check-in-result check-in-result-${verdict.tone}`} role="status">
      <span aria-hidden className="check-in-result-icon">
        {verdict.icon}
      </span>
      <div>
        <Typography.Title className="check-in-result-headline" level={2}>
          {verdict.headline}
        </Typography.Title>
        <Typography.Text className="check-in-result-advice">{verdict.advice}</Typography.Text>
        {seat ? (
          <div className="check-in-result-details">
            <strong>{seat}</strong>
            {result.buyerEmail ? <span>{result.buyerEmail}</span> : null}
            {result.outcome === 'ALREADY_USED' && result.usedAt ? (
              <span>First scanned at {formatTime(result.usedAt)}</span>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  )
}

function seatDescription(result: CheckInResult) {
  if (!result.sectionName || !result.seatLabel) {
    return null
  }
  return `${result.sectionName}, row ${result.rowLabel}, seat ${result.seatLabel}`
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { timeStyle: 'medium' }).format(new Date(value))
}
