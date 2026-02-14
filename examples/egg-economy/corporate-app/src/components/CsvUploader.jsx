import { useState } from 'react'
import { restPost } from '../api'

export default function CsvUploader({ endpoint, mapRow, label }) {
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [total, setTotal] = useState(0)
  const [errors, setErrors] = useState([])
  const [done, setDone] = useState(false)

  async function handleFile(e) {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    setProgress(0)
    setErrors([])
    setDone(false)

    const text = await file.text()
    const lines = text.trim().split('\n')
    if (lines.length < 2) {
      setErrors(['CSV file must have a header row and at least one data row.'])
      setUploading(false)
      return
    }

    const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''))
    const rows = lines.slice(1)
    setTotal(rows.length)

    const errs = []
    for (let i = 0; i < rows.length; i++) {
      const cells = rows[i].split(',').map(c => c.trim().replace(/^"|"$/g, ''))
      const obj = {}
      headers.forEach((h, j) => { obj[h] = cells[j] || '' })
      try {
        const mapped = mapRow(obj)
        await restPost(endpoint, mapped)
      } catch (err) {
        errs.push(`Row ${i + 2}: ${err.message}`)
      }
      setProgress(i + 1)
    }

    setErrors(errs)
    setDone(true)
    setUploading(false)
    e.target.value = ''
  }

  return (
    <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
      <label className="block text-sm font-medium text-slate-700 mb-2">{label || 'Upload CSV'}</label>
      <input
        type="file"
        accept=".csv"
        onChange={handleFile}
        disabled={uploading}
        className="block w-full text-sm text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 disabled:opacity-50"
      />
      {uploading && (
        <div className="mt-3">
          <div className="flex justify-between text-xs text-slate-500 mb-1">
            <span>Uploading...</span>
            <span>{progress} / {total}</span>
          </div>
          <div className="w-full bg-slate-200 rounded-full h-2">
            <div
              className="bg-blue-600 h-2 rounded-full transition-all"
              style={{ width: total > 0 ? `${(progress / total) * 100}%` : '0%' }}
            ></div>
          </div>
        </div>
      )}
      {done && (
        <div className={`mt-3 text-sm ${errors.length > 0 ? 'text-amber-700' : 'text-green-700'}`}>
          {errors.length > 0
            ? `Completed with ${errors.length} error(s) out of ${total} rows.`
            : `Successfully uploaded ${total} rows.`
          }
        </div>
      )}
      {errors.length > 0 && (
        <div className="mt-2 max-h-32 overflow-y-auto text-xs text-red-600 space-y-0.5">
          {errors.map((err, i) => <div key={i}>{err}</div>)}
        </div>
      )}
    </div>
  )
}
