import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { CreateUserRequest } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateUserPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateUserPage {

  form: UntypedFormGroup;

  constructor(
    formBuilder: UntypedFormBuilder,
    title: Title,
    private router: Router,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    title.setTitle('Create a User');
    this.form = formBuilder.group({
      name: new UntypedFormControl('', [Validators.required]),
      displayName: new UntypedFormControl('', [Validators.required]),
      email: new UntypedFormControl('', [Validators.required]),
      password: new UntypedFormControl(),
      passwordConfirmation: new UntypedFormControl(),
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: CreateUserRequest = {
      name: formValue.name,
      displayName: formValue.displayName,
      email: formValue.email,
    };
    if (formValue.password) {
      if (formValue.password !== formValue.passwordConfirmation) {
        alert('Password confirmation does not match password');
        return;
      }
      options.password = formValue.password;
    }
    this.yamcs.yamcsClient.createUser(options)
      .then(() => this.router.navigateByUrl(`/admin/iam/users/${formValue.name}`))
      .catch(err => this.messageService.showError(err));
  }
}
