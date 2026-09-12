package com.bits.facultyai.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.FacultyProfileEntity
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.GlassTextField
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val step by vm.step.collectAsStateWithLifecycle()
    // FIX: the Continue button read vm.fullName.value directly — a StateFlow
    // read without collection never recomposes, so the button stayed disabled
    // no matter what was typed. Collect it as Compose state instead.
    val fullName by vm.fullName.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val k = LocalKineticColors.current

    // Set when the user taps Continue on step 0 with an empty name — drives
    // the inline "name required" error instead of a silently dead button.
    // Reset whenever the step changes.
    var nameAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(step) { nameAttempted = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg)
            // Root NavHost already applies statusBarsPadding + imePadding
            // globally; onboarding only adds bottom system-area breathing room.
            .navigationBarsPadding(),
    ) {
        KineticDisplayText(
            text = when (step) {
                0 -> "WELCOME TO ACADORA"
                1 -> "YOUR SUBJECTS"
                2 -> "YOUR ASSISTANT"
                else -> "ACADORA"
            },
            style = KineticType.display.copy(fontSize = 40.sp),
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        Text(
            text = when (step) {
                0 -> "Let's set up your personal assistant"
                1 -> "Which subjects do you handle this semester?"
                2 -> "Your assistant remembers what matters to you"
                else -> ""
            },
            style = KineticType.body,
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.xl))

        val showNameError = step == 0 && nameAttempted && fullName.isBlank()
        when (step) {
            0 -> ProfileStep(vm = vm, showNameError = showNameError)
            1 -> SubjectsStep(vm = vm)
            2 -> AssistantStep(vm = vm)
        }

        Spacer(Modifier.height(KineticSpacing.xl))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.md)) {
            if (step > 0) {
                KineticGhostButton(text = "Back", onClick = { vm.previous() })
            }
            Spacer(Modifier.weight(1f))
            KineticGhostButton(text = "Skip", onClick = { scope.launch { vm.complete(onComplete) } })
            KineticButton(
                text = if (step == 2) "Ready" else "Continue",
                // Name is the one field that personalizes the whole app.
                enabled = step != 0 || fullName.isNotBlank(),
                onClick = {
                    if (step == 0 && fullName.isBlank()) {
                        nameAttempted = true
                    } else {
                        scope.launch { vm.next(onComplete) }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileStep(vm: OnboardingViewModel, showNameError: Boolean) {
    val k = LocalKineticColors.current
    // Collect field state here — a raw `flow.value` read in composition never
    // recomposes, which froze these fields while typing.
    val fullName by vm.fullName.collectAsStateWithLifecycle()
    val preferredName by vm.preferredName.collectAsStateWithLifecycle()
    val designation by vm.designation.collectAsStateWithLifecycle()
    val department by vm.department.collectAsStateWithLifecycle()
    Column {
        GlassTextField(value = fullName, onValueChange = vm::setFullName, hint = "FULL NAME", isError = showNameError)
        if (showNameError) {
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(
                text = "Please enter your name to continue — or tap SKIP.",
                style = KineticType.label,
                color = k.statusError,
            )
        }
        Spacer(Modifier.height(KineticSpacing.md))
        GlassTextField(value = preferredName, onValueChange = vm::setPreferredName, hint = "PREFERRED NAME (HOW YOUR ASSISTANT GREETS YOU)")
        Spacer(Modifier.height(KineticSpacing.md))
        GlassTextField(value = designation, onValueChange = vm::setDesignation, hint = "DESIGNATION (E.G. ASSISTANT PROFESSOR)")
        Spacer(Modifier.height(KineticSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            listOf("PROFESSOR", "ASSOC. PROFESSOR", "ASST. PROFESSOR").forEach { pick ->
                QuickPick(label = pick) { vm.setDesignation(pick) }
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))
        GlassTextField(value = department, onValueChange = vm::setDepartment, hint = "DEPARTMENT (E.G. ECE)")
        Spacer(Modifier.height(KineticSpacing.md))
        Text(
            text = "Only your name, designation and department personalize the app. Everything stays editable in MY PROFILE.",
            style = KineticType.label,
            color = k.mutedForeground,
        )
    }
}

/** Small tappable preset chip (designation quick-picks). */
@Composable
private fun QuickPick(label: String, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Box(
        modifier = Modifier
            .border(KineticBorder.hair, k.border)
            .clickable(onClick = onClick)
            .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.xs),
    ) {
        Text(
            text = label,
            style = KineticType.label,
            color = k.mutedForeground,
        )
    }
}

@Composable
private fun SubjectsStep(vm: OnboardingViewModel) {
    val k = LocalKineticColors.current
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    Column {
        KineticTextField(value = subjects, onValueChange = vm::setSubjects, hint = "e.g. DSP, Communication Systems, VLSI", minLines = 2, maxLines = 3)
        Spacer(Modifier.height(KineticSpacing.md))
        Text(
            text = "Your assistant uses this to understand questions like \"prepare something for my next class\".",
            style = KineticType.label,
            color = k.mutedForeground,
        )
    }
}

@Composable
private fun AssistantStep(vm: OnboardingViewModel) {
    val k = LocalKineticColors.current
    Column {
        Text(
            text = "WHAT YOUR ASSISTANT CAN DO",
            style = KineticType.labelBold,
            color = k.foreground,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        listOf(
            "Answers from your real timetable — never invents classes",
            "Finds your own notes and saved memories on request",
            "Drafts lesson plans and questions when you ask",
            "Remembers only what you allow, in MY MEMORY",
        ).forEach {
            Text(text = "— $it", style = KineticType.body, color = k.foreground)
            Spacer(Modifier.height(KineticSpacing.xs))
        }
    }
}
