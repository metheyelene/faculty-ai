package com.bits.facultyai.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.FacultyProfileEntity
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticTextField
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
    val scope = rememberCoroutineScope()
    val k = LocalKineticColors.current

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
                0 -> "FACULTY AI"
                1 -> "YOUR SUBJECTS"
                2 -> "YOUR ASSISTANT"
                else -> "FACULTY AI"
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

        when (step) {
            0 -> ProfileStep(vm = vm)
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
                // Name is the one field that personalizes the whole app —
                // require it on step 0 (Skip remains available).
                enabled = step != 0 || vm.fullName.value.isNotBlank(),
                onClick = { scope.launch { vm.next(onComplete) } },
            )
        }
    }
}

@Composable
private fun ProfileStep(vm: OnboardingViewModel) {
    val k = LocalKineticColors.current
    Column {
        KineticTextField(value = vm.fullName.value, onValueChange = vm::setFullName, hint = "FULL NAME")
        Spacer(Modifier.height(KineticSpacing.md))
        KineticTextField(value = vm.preferredName.value, onValueChange = vm::setPreferredName, hint = "PREFERRED NAME (HOW YOUR ASSISTANT GREETS YOU)")
        Spacer(Modifier.height(KineticSpacing.md))
        KineticTextField(value = vm.designation.value, onValueChange = vm::setDesignation, hint = "DESIGNATION")
        Spacer(Modifier.height(KineticSpacing.md))
        KineticTextField(value = vm.department.value, onValueChange = vm::setDepartment, hint = "DEPARTMENT")
        Spacer(Modifier.height(KineticSpacing.md))
        Text(
            text = "Only your name, designation and department personalize the app. Everything stays editable in Profile.",
            style = KineticType.label,
            color = k.mutedForeground,
        )
    }
}

@Composable
private fun SubjectsStep(vm: OnboardingViewModel) {
    val k = LocalKineticColors.current
    Column {
        KineticTextField(value = vm.subjects.value, onValueChange = vm::setSubjects, hint = "e.g. DSP, Communication Systems, VLSI", minLines = 2, maxLines = 3)
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
