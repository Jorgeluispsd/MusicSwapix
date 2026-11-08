import {Component}  from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

type PlaylistVisibility = 'PRIVATE' | 'PUBLIC' | 'UNLISTED';

@Component({
  selector: 'app-conversion-page',
  imports: [ReactiveFormsModule],
  templateUrl: './conversion-page.html',
  styleUrl: './conversion-page.css',
})
export class ConversionPage {
  readonly form = new FormGroup({
    sourcePlaylist: new FormControl('',{
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    destinationName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    visibility: new FormControl<PlaylistVisibility>('PRIVATE',{
      nonNullable: true,
    }),
  });
}
