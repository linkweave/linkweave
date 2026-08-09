import type { Command, Option } from 'commander'

import type { CompletionSource } from './completeCmd'

export const COMPLETION_SHELLS = ['bash', 'zsh', 'fish'] as const

export type CompletionShell = (typeof COMPLETION_SHELLS)[number]

interface SubcommandInfo {
  name: string
  description: string
}

interface OptionInfo {
  /** Both spellings of the flag, e.g. ['-f', '--format']. */
  flags: string[]
  description: string
  takesValue: boolean
  /** Completable values for options declared with .choices(). */
  choices?: string[]
}

interface CompletionNode {
  /** Subcommand path from the program root, e.g. ['bookmarks', 'add']. */
  path: string[]
  subcommands: SubcommandInfo[]
  options: OptionInfo[]
  /** Completable values of positional arguments declared with .choices(). */
  argChoices: string[]
  /** Server-backed source per positional slot; see DYNAMIC_ARG_SOURCES. */
  argSources: readonly (CompletionSource | undefined)[]
}

function toOptionInfo(option: Option): OptionInfo {
  const flags: string[] = []
  if (option.short) flags.push(option.short)
  if (option.long) flags.push(option.long)
  return {
    flags,
    description: option.description,
    takesValue: option.required || option.optional,
    choices: option.argChoices,
  }
}

/**
 * Flattens the commander command tree into one node per command path, so the
 * shell scripts below are always generated from the real program definition —
 * a new command or flag shows up in completions without touching this file.
 */
export function collectNodes(root: Command): CompletionNode[] {
  const nodes: CompletionNode[] = []
  const visit = (cmd: Command, path: string[]): void => {
    // visibleCommands filters out hidden ones (`__complete`), which must never
    // be offered as a suggestion.
    const subcommands = cmd.createHelp().visibleCommands(cmd)
    nodes.push({
      path,
      subcommands: subcommands.map((sub) => ({ name: sub.name(), description: sub.description() })),
      options: cmd.options.filter((option) => !option.hidden).map(toOptionInfo),
      argChoices: cmd.registeredArguments.flatMap((argument) => argument.argChoices ?? []),
      argSources: DYNAMIC_ARG_SOURCES[path.join(' ')] ?? [],
    })
    for (const sub of subcommands) visit(sub, [...path, sub.name()])
  }
  visit(root, [])
  return nodes
}

/** The words to offer once the user has typed the node's command path. */
function wordsFor(node: CompletionNode, globalOptions: OptionInfo[]): string[] {
  const words = [
    ...node.subcommands.map((sub) => sub.name),
    // Commander registers an implicit `help` subcommand on every parent.
    ...(node.subcommands.length > 0 ? ['help'] : []),
    ...node.argChoices,
    ...node.options.flatMap((option) => option.flags),
    // Global options parse after subcommand names too (commander default).
    ...globalOptions.flatMap((option) => option.flags),
    '-h',
    '--help',
  ]
  return [...new Set(words)]
}

/** Nodes ordered deepest-first so prefix matching hits the most specific one. */
function orderedNodes(nodes: CompletionNode[]): { deep: CompletionNode[]; root: CompletionNode } {
  const root = nodes.find((node) => node.path.length === 0)
  if (!root) throw new Error('command tree has no root node')
  const deep = nodes.filter((node) => node.path.length > 0).sort((a, b) => b.path.length - a.path.length)
  return { deep, root }
}

/** Flags that consume the following word, e.g. `--collection Work`. */
function valueFlags(nodes: CompletionNode[]): string[] {
  const flags = nodes.flatMap((node) => node.options.filter((o) => o.takesValue).flatMap((o) => o.flags))
  return [...new Set(flags)]
}

/**
 * Options whose values live on the server and are completed by calling back
 * into the CLI (`linkweave __complete`).
 *
 * `--tags` is deliberately absent: it takes a comma-separated list, so useful
 * completion would have to replace only the text after the last comma, which
 * none of the three shells makes cheap. `--tag` (singular, one value) is here.
 */
const DYNAMIC_SOURCES: Readonly<Record<string, CompletionSource>> = {
  '--collection': 'collections',
  '--tag': 'tags',
  '--folder': 'folders',
}

/**
 * Positional arguments whose values live on the server, keyed by command path
 * with one entry per argument slot (`undefined` leaves that slot alone).
 *
 * Keyed by path rather than by argument name because the decision is per slot,
 * not per spelling: every `rename` takes an existing thing followed by a
 * `<new-name>`, and offering the existing names for the second one would be
 * actively wrong. Listing the paths keeps each of those calls explicit.
 *
 * `folders create <path>` is included even though it rejects a path that
 * already exists: the value of completing there is building a nested path on
 * top of an existing one (`Langs/<TAB>` → `Langs/Rust`, then type `/New`).
 *
 * Bookmark and trashbin IDs are deliberately absent — a bare list of UUIDs is
 * no use without a title beside it, and `__complete` prints one value per line
 * with nowhere to put one.
 */
const DYNAMIC_ARG_SOURCES: Readonly<Record<string, readonly (CompletionSource | undefined)[]>> = {
  'collections rename': ['collections'],
  'collections default': ['collections'],
  'collections rm': ['collections'],
  'tags rename': ['tags'],
  'tags rm': ['tags'],
  'folders create': ['folders'],
  'folders rename': ['folders'],
  'folders mv': ['folders', 'folders'],
  'folders rm': ['folders'],
}

/**
 * The per-slot sources as shell words. `-` marks a slot that completes
 * nothing, so the array stays positional without empty elements to quote.
 */
function argSourceWords(node: CompletionNode): string {
  return node.argSources.map((source) => source ?? '-').join(' ')
}

/** Options backed by a `__complete` source, keyed by their flag alternation. */
function dynamicArms(nodes: CompletionNode[]): { pattern: string; source: CompletionSource }[] {
  const byPattern = new Map<string, CompletionSource>()
  for (const node of nodes) {
    for (const option of node.options) {
      const long = option.flags.find((flag) => flag.startsWith('--'))
      const source = long === undefined ? undefined : DYNAMIC_SOURCES[long]
      if (source !== undefined) byPattern.set(option.flags.join('|'), source)
    }
  }
  return [...byPattern.entries()].map(([pattern, source]) => ({ pattern, source }))
}

/** Options with a fixed value set, keyed by their `-f|--format` alternation. */
function choiceArms(nodes: CompletionNode[]): { pattern: string; values: string[] }[] {
  const byPattern = new Map<string, Set<string>>()
  for (const node of nodes) {
    for (const option of node.options) {
      if (!option.choices || option.choices.length === 0) continue
      const pattern = option.flags.join('|')
      const values = byPattern.get(pattern) ?? new Set()
      for (const value of option.choices) values.add(value)
      byPattern.set(pattern, values)
    }
  }
  return [...byPattern.entries()].map(([pattern, values]) => ({ pattern, values: [...values] }))
}

export function completionScript(shell: CompletionShell, root: Command): string {
  const nodes = collectNodes(root)
  switch (shell) {
    case 'bash':
      return bashScript(nodes)
    case 'zsh':
      return zshScript(nodes)
    case 'fish':
      return fishScript(nodes)
  }
}

function bashScript(nodes: CompletionNode[]): string {
  const { deep, root } = orderedNodes(nodes)
  const skipPattern = valueFlags(nodes).join('|')
  const prevArms = [
    ...choiceArms(nodes).map(
      ({ pattern, values }) =>
        `    ${pattern}) COMPREPLY=($(compgen -W "${values.join(' ')}" -- "$cur")); return ;;`,
    ),
    ...dynamicArms(nodes).map(
      ({ pattern, source }) => `    ${pattern}) __linkweave_values ${source}; return ;;`,
    ),
  ].join('\n')
  const contextArms = deep
    .map((node) => {
      const words = `words="${wordsFor(node, root.options).join(' ')}"`
      // Only nodes with a server-backed slot carry the extra state; every
      // other arm leaves the defaults set before the case in place.
      const positional = node.argSources.some((source) => source !== undefined)
        ? `; pathlen=${node.path.length}; argsources=(${argSourceWords(node)})`
        : ''
      return `    "${node.path.join(' ')}"*) ${words}${positional} ;;`
    })
    .join('\n')
  return `# bash completion for linkweave — generated by 'linkweave completion bash'

# Completes an option value from the server via the hidden __complete command.
# Candidates are newline-separated because collection, tag and folder names
# may contain spaces; each is %q-escaped so inserting one keeps it a single
# word. Failures are silent — __complete exits 0 with no output.
#
# read in a loop rather than mapfile: macOS still ships bash 3.2, where
# mapfile does not exist.
__linkweave_values() {
  local collection="" i value
  for ((i = 1; i < COMP_CWORD; i++)); do
    case "\${COMP_WORDS[i]}" in
      # '=' is in COMP_WORDBREAKS, so '--collection=Work' normally arrives as
      # three words. Both tokenisations are handled: a user with a trimmed
      # COMP_WORDBREAKS gets it as one.
      --collection)
        if [[ "\${COMP_WORDS[i + 1]}" == "=" ]]; then
          collection="\${COMP_WORDS[i + 2]}"
        else
          collection="\${COMP_WORDS[i + 1]}"
        fi
        ;;
      --collection=*) collection="\${COMP_WORDS[i]#--collection=}" ;;
    esac
  done
  COMPREPLY=()
  while IFS= read -r value; do
    COMPREPLY+=("$(printf '%q' "$value")")
  done < <(
    if [[ -n "$collection" ]]; then
      linkweave __complete "$1" --collection "$collection" -- "$cur" 2>/dev/null
    else
      linkweave __complete "$1" -- "$cur" 2>/dev/null
    fi
  )
}

_linkweave() {
  local cur prev words pathlen=0
  local -a argsources=()
  cur="\${COMP_WORDS[COMP_CWORD]}"
  prev="\${COMP_WORDS[COMP_CWORD - 1]}"

  case "$prev" in
${prevArms}
  esac

  # The command context is every previous word that is not a flag or a flag's
  # value, e.g. 'bookmarks add' in 'linkweave -s URL bookmarks add --title T'.
  local -a cmdwords=()
  local i w skip=0
  for ((i = 1; i < COMP_CWORD; i++)); do
    w="\${COMP_WORDS[i]}"
    if ((skip)); then ((skip--)); continue; fi
    case "$w" in
      # Skip two for the split '--flag = value' form, so the value is not
      # mistaken for a subcommand name.
      ${skipPattern})
        if [[ "\${COMP_WORDS[i + 1]}" == "=" ]]; then skip=2; else skip=1; fi
        ;;
      -*) ;;
      *) cmdwords+=("$w") ;;
    esac
  done

  case "\${cmdwords[*]}" in
${contextArms}
    *) words="${wordsFor(root, []).join(' ')}" ;;
  esac

  # Which positional slot the cursor sits in: the command words typed so far,
  # less the matched command's own path. 'folders mv <path> <destination>'
  # completes folder paths in both slots; a '<new-name>' has none and falls
  # through to the flag list, as does anything already starting with '-'.
  local idx=$((\${#cmdwords[@]} - pathlen))
  if ((idx >= 0)) && ((idx < \${#argsources[@]})); then
    local source="\${argsources[idx]}"
    if [[ "$source" != "-" && "$cur" != -* ]]; then
      __linkweave_values "$source"
      return
    fi
  fi

  COMPREPLY=($(compgen -W "$words" -- "$cur"))
}
complete -F _linkweave linkweave
`
}

function zshScript(nodes: CompletionNode[]): string {
  const { deep, root } = orderedNodes(nodes)
  const skipPattern = valueFlags(nodes).join('|')
  const prevArms = [
    ...choiceArms(nodes).map(
      ({ pattern, values }) => `    ${pattern}) compadd -- ${values.join(' ')}; return ;;`,
    ),
    ...dynamicArms(nodes).map(
      ({ pattern, source }) => `    ${pattern}) __linkweave_values ${source}; return ;;`,
    ),
  ].join('\n')
  const contextArms = deep
    .map((node) => {
      const completions = `completions=(${wordsFor(node, root.options).join(' ')})`
      const positional = node.argSources.some((source) => source !== undefined)
        ? `; pathlen=${node.path.length}; argsources=(${argSourceWords(node)})`
        : ''
      return `    "${node.path.join(' ')}"*) ${completions}${positional} ;;`
    })
    .join('\n')
  return `# zsh completion for linkweave — generated by 'linkweave completion zsh'
# Load after compinit, e.g. in ~/.zshrc: eval "$(linkweave completion zsh)"

# Completes an option value from the server via the hidden __complete command.
# \${(f)...} splits on newlines only, so names containing spaces survive, and
# compadd quotes them on insertion. Failures are silent.
__linkweave_values() {
  local collection="" i
  local -a values
  for ((i = 1; i < CURRENT; i++)); do
    # zsh keeps '--collection=Work' as a single word, unlike bash.
    case "\${words[i]}" in
      --collection) collection="\${words[i + 1]}" ;;
      --collection=*) collection="\${words[i]#--collection=}" ;;
    esac
  done
  if [[ -n "$collection" ]]; then
    values=(\${(f)"$(linkweave __complete "$1" --collection "$collection" -- "\${words[CURRENT]}" 2>/dev/null)"})
  else
    values=(\${(f)"$(linkweave __complete "$1" -- "\${words[CURRENT]}" 2>/dev/null)"})
  fi
  (( \${#values} )) && compadd -- "\${values[@]}"
}

_linkweave() {
  local -a cmdwords completions argsources
  local w prev skip=0 pathlen=0
  (( CURRENT > 2 )) && prev="\${words[CURRENT - 1]}"

  case "$prev" in
${prevArms}
  esac

  for w in "\${(@)words[2,CURRENT - 1]}"; do
    if ((skip)); then skip=0; continue; fi
    case "$w" in
      ${skipPattern}) skip=1 ;;
      -*) ;;
      *) cmdwords+=("$w") ;;
    esac
  done

  case "\${(j: :)cmdwords}" in
${contextArms}
    *) completions=(${wordsFor(root, []).join(' ')}) ;;
  esac

  # As in the bash script: the positional slot is the command words typed so
  # far less the matched command's path. zsh arrays are 1-based, hence the +1.
  local idx=$(( \${#cmdwords[@]} - pathlen ))
  if (( idx >= 0 )) && (( idx < \${#argsources[@]} )); then
    local source="\${argsources[idx + 1]}"
    if [[ "$source" != "-" && "\${words[CURRENT]}" != -* ]]; then
      __linkweave_values "$source"
      return
    fi
  fi

  compadd -- "\${completions[@]}"
}
compdef _linkweave linkweave
`
}

function fishEscape(text: string): string {
  return text.replace(/[\\"$]/g, '\\$&')
}

function fishScript(nodes: CompletionNode[]): string {
  const { deep, root } = orderedNodes(nodes)
  const sources = new Map(
    dynamicArms(nodes).flatMap(({ pattern, source }) =>
      pattern.split('|').map((flag) => [flag, source] as const),
    ),
  )
  const lines = [
    "# fish completion for linkweave — generated by 'linkweave completion fish'",
    'complete -c linkweave -f',
    '',
    '# Completes an option value from the server via the hidden __complete',
    '# command, forwarding any --collection already typed on the line. fish',
    '# splits command output on newlines and escapes it on insertion, so names',
    '# containing spaces need no extra handling. Failures are silent.',
    'function __linkweave_values',
    '    set -l parts (commandline -opc 2>/dev/null)',
    '    set -l collection ""',
    '    set -l previous ""',
    '    # Tracking the previous word avoids indexing, which errors on an',
    '    # empty command line (fish arrays are 1-based).',
    '    for part in $parts',
    '        # fish keeps --collection=Work as a single token, unlike bash.',
    '        if test "$previous" = "--collection"',
    '            set collection $part',
    '        else if string match -q -- "--collection=*" $part',
    '            set collection (string replace -- "--collection=" "" $part)',
    '        end',
    '        set previous $part',
    '    end',
    '    if test -n "$collection"',
    '        linkweave __complete $argv[1] --collection $collection 2>/dev/null',
    '    else',
    '        linkweave __complete $argv[1] 2>/dev/null',
    '    end',
    'end',
    '',
    '# Which positional slot the cursor is in, for a command whose own path is',
    '# $argv[1] words long. fish has no shared dispatch the way the bash and zsh',
    '# scripts do, so the count is recomputed per condition. Flags and their',
    '# values are skipped, so `--collection Work` does not read as a positional.',
    'function __linkweave_positional_index',
    '    set -l parts (commandline -opc 2>/dev/null)',
    '    set -l count 0',
    '    set -l skip 0',
    '    set -l first 1',
    '    for w in $parts',
    '        if test $first -eq 1',
    '            # parts[1] is the command name itself.',
    '            set first 0',
    '        else if test $skip -eq 1',
    '            set skip 0',
    `        else if contains -- $w ${valueFlags(nodes).join(' ')}`,
    '            set skip 1',
    '        else if string match -q -- "-*" $w',
    '            # A flag that takes no value; nothing to skip.',
    '        else',
    '            set count (math $count + 1)',
    '        end',
    '    end',
    '    echo (math $count - $argv[1])',
    'end',
    '',
  ]
  const optionLine = (option: OptionInfo, condition?: string): string => {
    const parts = ['complete -c linkweave']
    if (condition) parts.push(`-n "${condition}"`)
    for (const flag of option.flags) {
      parts.push(flag.startsWith('--') ? `-l ${flag.slice(2)}` : `-s ${flag.slice(1)}`)
    }
    const source = option.flags.map((flag) => sources.get(flag)).find((value) => value !== undefined)
    if (option.choices && option.choices.length > 0) parts.push(`-x -a "${option.choices.join(' ')}"`)
    else if (source !== undefined) parts.push(`-x -a "(__linkweave_values ${source})"`)
    else if (option.takesValue) parts.push('-r')
    if (option.description) parts.push(`-d "${fishEscape(option.description)}"`)
    return parts.join(' ')
  }
  for (const option of root.options) lines.push(optionLine(option))
  for (const sub of root.subcommands) {
    lines.push(
      `complete -c linkweave -n __fish_use_subcommand -a ${sub.name} -d "${fishEscape(sub.description)}"`,
    )
  }
  for (const node of deep) {
    // Require every path segment, not just the last: 'bookmarks list' and
    // 'collections list' share their final segment.
    const seen = node.path.map((segment) => `__fish_seen_subcommand_from ${segment}`).join('; and ')
    for (const sub of node.subcommands) {
      const notSeen = `not __fish_seen_subcommand_from ${[...node.subcommands.map((s) => s.name), 'help'].join(' ')}`
      lines.push(
        `complete -c linkweave -n "${seen}; and ${notSeen}" -a ${sub.name} -d "${fishEscape(sub.description)}"`,
      )
    }
    for (const option of node.options) lines.push(optionLine(option, seen))
    if (node.argChoices.length > 0) {
      lines.push(`complete -c linkweave -n "${seen}" -a "${node.argChoices.join(' ')}"`)
    }
    // One line per server-backed slot, each gated on the cursor being in it.
    node.argSources.forEach((source, slot) => {
      if (source === undefined) return
      const inSlot = `test (__linkweave_positional_index ${node.path.length}) -eq ${slot}`
      lines.push(
        `complete -c linkweave -n "${seen}; and ${inSlot}" -f -a "(__linkweave_values ${source})"`,
      )
    })
  }
  return lines.join('\n') + '\n'
}
