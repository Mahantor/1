package com.example

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootHelper {

    private const val TAG = "RootHelper"

    /**
     * Checks if the app can obtain root access by running standard su.
     */
    fun checkRootPermission(): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "checkRootPermission error: ${e.message}")
            false
        } finally {
            try {
                os?.close()
            } catch (ignored: Exception) {}
            try {
                process?.destroy()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Executes a single command using standard root shell 'su -c "command"'.
     */
    fun runAsRoot(command: String): ShellResult {
        var process: Process? = null
        var stdInput: BufferedReader? = null
        var stdError: BufferedReader? = null
        return try {
            Log.d(TAG, "Executing root command: su -c \"$command\"")
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            
            stdInput = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            stdError = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            var line: String?
            while (stdInput.readLine().also { line = it } != null) {
                stdoutBuilder.append(line).append("\n")
            }
            while (stdError.readLine().also { line = it } != null) {
                stderrBuilder.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            val output = stdoutBuilder.toString().trim()
            val error = stderrBuilder.toString().trim()

            Log.d(TAG, "Command finished. Exit code: $exitCode, Out: $output, Err: $error")
            ShellResult(exitCode = exitCode, stdout = output, stderr = error)
        } catch (e: Exception) {
            Log.e(TAG, "runAsRoot failed: ${e.message}")
            ShellResult(exitCode = -1, stdout = "", stderr = e.message ?: "Execution failed")
        } finally {
            try { stdInput?.close() } catch (ignored: Exception) {}
            try { stdError?.close() } catch (ignored: Exception) {}
            try { process?.destroy() } catch (ignored: Exception) {}
        }
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}
