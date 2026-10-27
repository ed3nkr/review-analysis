import { FileText } from 'lucide-react'
import { Button, StateBlock } from '../components/ui'

export default function ComingSoon({ title, note }) {
  return (
    <div className="page">
      <div className="card card-state">
        <StateBlock
          icon={FileText}
          kicker={title}
          title="아직 만드는 중이에요"
          text={note}
          action={
            <Button variant="secondary" to="/stores">
              매장 화면으로
            </Button>
          }
        />
      </div>
    </div>
  )
}
