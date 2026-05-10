const AVATAR_COLORS = [
  '#e11d48', // rose
  '#ea580c', // orange
  '#ca8a04', // yellow
  '#16a34a', // green
  '#0891b2', // cyan
  '#2563eb', // blue
  '#7c3aed', // violet
  '#db2777', // pink
  '#0f766e', // teal
  '#9333ea', // purple
]

export function avatarColor(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + (hash << 5) - hash
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]!
}
