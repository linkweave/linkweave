import { run } from './run'

// Setting exitCode rather than calling process.exit(): writes to a piped
// stdout/stderr are asynchronous on POSIX, and process.exit() discards
// whatever has not been flushed yet. Returning lets node drain and then exit
// with the code on its own.
void run(process.argv).then((code) => {
  process.exitCode = code
})
